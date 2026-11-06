package com.adn.dabaeum.credential;

import static org.assertj.core.api.Assertions.assertThat;

import com.adn.dabaeum.credential.domain.PendingSigningKeyRequest;
import com.adn.dabaeum.credential.domain.SigningKeyRequestQueue;
import com.adn.dabaeum.institution.domain.InstitutionSigningKeyRequest;
import com.adn.dabaeum.institution.domain.InstitutionSigningKeyRequestRepository;
import com.adn.dabaeum.support.RemotePostgresIntegrationTestSupport;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 기관 서명키 생성 요청을 outbox 에 쌓고, 워커가 lease 를 걸어 가져가고, 결과를 남기는 SQL.
 * 테스트 트랜잭션 안이라 다른 연결과의 잠금 경합은 여기서 보지 않는다.
 */
class SigningKeyRequestQueueIntegrationTest extends RemotePostgresIntegrationTestSupport {

    // 실제 대기 이벤트보다 앞서 가져오도록 아주 이른 시각으로 쌓는다.
    private static final Instant REQUESTED_AT = Instant.parse("2000-01-01T00:00:00Z");
    private static final Instant NOW = Instant.parse("2000-01-01T00:01:00Z");
    private static final Instant LEASE_UNTIL = NOW.plusSeconds(600);
    private static final UUID ADMIN_ID = UUID.fromString("ffffffff-0000-0000-0000-000000000001");

    @Autowired InstitutionSigningKeyRequestRepository requests;
    @Autowired SigningKeyRequestQueue queue;

    @Test
    void savesRequestAsPendingOutboxEventWithJsonPayload() {
        UUID institutionId = UUID.randomUUID();
        UUID eventId = save(institutionId);

        assertThat(jdbcTemplate.queryForMap("""
            SELECT aggregate_type, aggregate_id::text AS aggregate_id, event_type, status,
                   payload->>'institutionId' AS institution_id, payload->>'requestedBy' AS requested_by
              FROM tb_outbox_events WHERE id = ?
            """, eventId))
            .containsEntry("aggregate_type", "INSTITUTION")
            .containsEntry("aggregate_id", institutionId.toString())
            .containsEntry("event_type", "INSTITUTION_SIGNING_KEYS_REQUESTED")
            .containsEntry("status", "PENDING")
            .containsEntry("institution_id", institutionId.toString())
            .containsEntry("requested_by", ADMIN_ID.toString());
    }

    @Test
    void claimHidesTheRequestUntilTheLeaseEndsThenReleasesIt() {
        UUID institutionId = UUID.randomUUID();
        UUID eventId = save(institutionId);

        assertThat(claimIds(NOW, LEASE_UNTIL)).contains(eventId);
        assertThat(claimIds(LEASE_UNTIL.minusSeconds(1), LEASE_UNTIL.plusSeconds(1)))
            .doesNotContain(eventId);
        assertThat(claimIds(LEASE_UNTIL, LEASE_UNTIL.plusSeconds(600))).contains(eventId);
    }

    @Test
    void doesNotClaimOtherEventTypes() {
        UUID completionEvent = UUID.randomUUID();
        jdbcTemplate.update("""
            INSERT INTO tb_outbox_events (id, aggregate_type, aggregate_id, event_type, payload,
                                          status, occurred_at, created_at)
            VALUES (?, 'COMPLETION', ?, 'COMPLETION_CONFIRMED', '{}'::jsonb, 'PENDING', ?, ?)
            """, completionEvent, UUID.randomUUID(), java.sql.Timestamp.from(REQUESTED_AT),
            java.sql.Timestamp.from(REQUESTED_AT));

        assertThat(claimIds(NOW, LEASE_UNTIL)).doesNotContain(completionEvent);
    }

    @Test
    void recordsResultsOnlyWhileHoldingTheLease() {
        UUID rescheduled = claimed();
        UUID done = claimed();
        UUID failed = claimed();

        assertThat(queue.reschedule(rescheduled, LEASE_UNTIL, 2, NOW.plusSeconds(300), "OPENBAO_UNAVAILABLE"))
            .isTrue();
        assertThat(queue.markDone(done, LEASE_UNTIL, NOW)).isTrue();
        assertThat(queue.markFailed(failed, LEASE_UNTIL, 5, "OPENBAO_UNAVAILABLE")).isTrue();

        assertThat(row(rescheduled))
            .containsEntry("status", "PENDING")
            .containsEntry("retry_count", 2)
            .containsEntry("error_message", "OPENBAO_UNAVAILABLE");
        assertThat(row(done)).containsEntry("status", "PUBLISHED");
        assertThat(row(failed))
            .containsEntry("status", "FAILED")
            .containsEntry("retry_count", 5);

        // lease 를 잃은(다른 서버가 다시 가져간) 뒤의 늦은 기록은 반영되지 않는다.
        assertThat(queue.markDone(failed, LEASE_UNTIL, NOW)).isFalse();
        assertThat(queue.markFailed(done, LEASE_UNTIL, 1, "LATE")).isFalse();
        assertThat(row(failed)).containsEntry("status", "FAILED");
        assertThat(row(done)).containsEntry("status", "PUBLISHED");
    }

    private UUID claimed() {
        UUID eventId = save(UUID.randomUUID());
        assertThat(claimIds(NOW, LEASE_UNTIL)).contains(eventId);
        return eventId;
    }

    private List<UUID> claimIds(Instant now, Instant leaseUntil) {
        return queue.claim(1000, now, leaseUntil).stream().map(PendingSigningKeyRequest::eventId).toList();
    }

    private UUID save(UUID institutionId) {
        UUID eventId = UUID.randomUUID();
        requests.save(new InstitutionSigningKeyRequest(eventId, institutionId, ADMIN_ID, REQUESTED_AT));
        return eventId;
    }

    private Map<String, Object> row(UUID eventId) {
        return jdbcTemplate.queryForMap(
            "SELECT status, retry_count, error_message FROM tb_outbox_events WHERE id = ?", eventId);
    }
}
