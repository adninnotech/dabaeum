package com.adn.dabaeum.credential;

import static org.assertj.core.api.Assertions.assertThat;

import com.adn.dabaeum.credential.application.CredentialEligibilityConsumer;
import com.adn.dabaeum.credential.domain.CompletionConfirmedOutboxEvent;
import com.adn.dabaeum.credential.domain.CompletionOutboxEventRepository;
import com.adn.dabaeum.support.RemotePostgresIntegrationTestSupport;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;

class CredentialEligibilityConsumerIntegrationTest extends RemotePostgresIntegrationTestSupport {

    private static final Instant NOW = Instant.parse("2026-08-05T00:00:00Z");

    @Autowired
    CredentialEligibilityConsumer consumer;

    @Autowired
    CompletionOutboxEventRepository outboxRepository;

    @Test
    void consumesOnlyDuePendingCompletionEventsAndCreatesOneGroup() {
        Fixture eligible = insertCompletedFixture();
        UUID dueEventId = insertOutbox(eligible.completionId(), eligible.enrollmentId(),
            eligible.courseId(), "COMPLETION_CONFIRMED", "PENDING", null);
        Fixture future = insertCompletedFixture();
        UUID futureEventId = insertOutbox(future.completionId(), future.enrollmentId(),
            future.courseId(), "COMPLETION_CONFIRMED", "PENDING", NOW.plusSeconds(1));
        Fixture wrongType = insertCompletedFixture();
        UUID wrongTypeEventId = insertOutbox(wrongType.completionId(), wrongType.enrollmentId(),
            wrongType.courseId(), "COURSE_CREATED", "PENDING", null);
        Fixture published = insertCompletedFixture();
        UUID publishedEventId = insertOutbox(published.completionId(), published.enrollmentId(),
            published.courseId(), "COMPLETION_CONFIRMED", "PUBLISHED", null);
        Fixture failed = insertCompletedFixture();
        UUID failedEventId = insertOutbox(failed.completionId(), failed.enrollmentId(),
            failed.courseId(), "COMPLETION_CONFIRMED", "FAILED", null);

        // 공유 개발 DB 에는 다른 대기 이벤트가 있을 수 있으므로 반환 건수 대신
        // 이 테스트가 만든 이벤트의 상태만 본다. 소비된 다른 건은 트랜잭션과 함께 롤백된다.
        drainDuePendingEvents();
        assertThat(countGroups(eligible.completionId())).isEqualTo(1);
        assertThat(statusOf(dueEventId)).isEqualTo("PUBLISHED");
        assertThat(statusOf(futureEventId)).isEqualTo("PENDING");
        assertThat(statusOf(wrongTypeEventId)).isEqualTo("PENDING");
        assertThat(statusOf(publishedEventId)).isEqualTo("PUBLISHED");
        assertThat(statusOf(failedEventId)).isEqualTo("FAILED");
        assertThat(consumer.consumeBatch(10, NOW)).isZero();
        assertThat(countGroups(eligible.completionId())).isEqualTo(1);
    }

    @Test
    void rollsBackACompletionStatementFailureToSavepointBeforeRescheduling() {
        Fixture fixture = insertCompletedFixture();
        UUID eventId = insertOutbox(fixture.completionId(), fixture.enrollmentId(),
            fixture.courseId(), "COMPLETION_CONFIRMED", "PENDING", null);

        // PostgreSQL DDL은 트랜잭션을 지원한다. 이름 변경은 이 테스트 트랜잭션과 함께
        // 롤백되고, 실패한 매퍼 구문은 소비자의 저장점 복구 동작을 검증한다.
        jdbcTemplate.execute("ALTER TABLE tb_completions RENAME COLUMN status TO task3_status_failure");

        Instant beforeConsume = Instant.now();
        assertThat(consumer.consumeBatch(1, NOW)).isEqualTo(1);
        assertThat(statusOf(eventId)).isEqualTo("PENDING");
        assertThat(retryCountOf(eventId)).isEqualTo(1);
        assertThat(nextRetryAtOf(eventId).toInstant())
            .isBetween(beforeConsume.plusSeconds(30), Instant.now().plusSeconds(31));
        assertThat(errorCodeOf(eventId)).isEqualTo("COMPLETION_OUTBOX_PROCESSING_FAILED");
    }

    @Test
    void claimSqlUsesSkipLockedAndOnlyExplicitColumns() throws Exception {
        String sql = new String(new ClassPathResource(
            "mybatis/mapper/credential/CompletionOutboxEventMapper.xml")
            .getInputStream().readAllBytes(), StandardCharsets.UTF_8);

        assertThat(sql).contains(
            "event_type = 'COMPLETION_CONFIRMED'",
            "status = 'PENDING'",
            "next_retry_at IS NULL OR next_retry_at &lt;= #{now}",
            "LIMIT #{limit}",
            "FOR UPDATE SKIP LOCKED");
        assertThat(sql).doesNotContain("SELECT *", "${");
    }

    @Test
    void claimsByLimitThenPersistsPublishedRetryAndFailedStates() {
        Fixture first = insertCompletedFixture();
        Fixture second = insertCompletedFixture();
        UUID firstEventId = insertOutbox(first.completionId(), first.enrollmentId(), first.courseId(),
            "COMPLETION_CONFIRMED", "PENDING", null);
        UUID secondEventId = insertOutbox(second.completionId(), second.enrollmentId(), second.courseId(),
            "COMPLETION_CONFIRMED", "PENDING", null);

        // LIMIT 은 누구의 행이든 1건만 돌려주는지로 확인하고, 상태 전이는 이 테스트가 만든
        // 두 이벤트로만 검증한다. 공유 개발 DB 의 다른 대기 이벤트가 결과에 섞여도 무관하다.
        assertThat(outboxRepository.claimPending(1, NOW)).hasSize(1);
        assertThat(claimOwn(NOW, firstEventId, secondEventId))
            .containsExactlyInAnyOrder(firstEventId, secondEventId);

        UUID claimedFirstId = firstEventId;
        UUID remainingEventId = secondEventId;
        outboxRepository.markPublished(claimedFirstId, NOW);
        assertThat(claimOwn(NOW, firstEventId, secondEventId)).containsExactly(remainingEventId);

        outboxRepository.reschedule(remainingEventId, 1, NOW.plusSeconds(30),
            "COMPLETION_OUTBOX_PROCESSING_FAILED");
        assertThat(claimOwn(NOW, firstEventId, secondEventId)).isEmpty();
        assertThat(claimOwn(NOW.plusSeconds(30), firstEventId, secondEventId))
            .containsExactly(remainingEventId);
        outboxRepository.markFailed(remainingEventId, 5, "COMPLETION_OUTBOX_PROCESSING_FAILED");

        assertThat(statusOf(claimedFirstId)).isEqualTo("PUBLISHED");
        assertThat(statusOf(remainingEventId)).isEqualTo("FAILED");
        assertThat(retryCountOf(remainingEventId)).isEqualTo(5);
        assertThat(errorCodeOf(remainingEventId)).isEqualTo("COMPLETION_OUTBOX_PROCESSING_FAILED");
    }

    private Fixture insertCompletedFixture() {
        UUID institutionId = UUID.randomUUID();
        jdbcTemplate.update(
            "INSERT INTO tb_institutions (id, institution_code, name) VALUES (?, ?, ?)",
            institutionId, uniqueCode("institution"), uniqueCode("institution-name"));
        UUID userId = UUID.randomUUID();
        String userCode = uniqueCode("user");
        jdbcTemplate.update("INSERT INTO tb_users (id, name, email) VALUES (?, ?, ?)",
            userId, userCode, userCode + "@example.test");
        UUID courseId = UUID.randomUUID();
        jdbcTemplate.update("""
            INSERT INTO tb_courses (
                id, institution_id, course_code, title, education_type,
                start_date, end_date, capacity
            ) VALUES (?, ?, ?, ?, 'OFFLINE', ?, ?, 20)
            """, courseId, institutionId, uniqueCode("course"), uniqueCode("course-title"),
            LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));
        UUID enrollmentId = UUID.randomUUID();
        jdbcTemplate.update("""
            INSERT INTO tb_enrollments (id, course_id, user_id, status, approved_at)
            VALUES (?, ?, ?, 'APPROVED', ?)
            """, enrollmentId, courseId, userId, OffsetDateTime.parse("2026-08-01T00:00:00Z"));
        UUID completionId = UUID.randomUUID();
        jdbcTemplate.update("""
            INSERT INTO tb_completions (
                id, enrollment_id, status, attendance_rate, completed_minutes,
                evaluated_at, completed_at, confirmed_by, confirmed_at
            ) VALUES (?, ?, 'COMPLETED', 100.00, 60, ?, ?, ?, ?)
            """, completionId, enrollmentId, offset(NOW), offset(NOW), userId, offset(NOW));
        return new Fixture(completionId, enrollmentId, courseId);
    }

    /** 처리 가능한 대기 이벤트를 전부 소비한다. 소비 결과는 테스트 트랜잭션과 함께 롤백된다. */
    private void drainDuePendingEvents() {
        for (int i = 0; i < 100; i++) {
            if (consumer.consumeBatch(10, NOW) == 0) {
                return;
            }
        }
        throw new IllegalStateException("due pending events did not drain within 100 batches");
    }

    /** 대기 이벤트 중 이 테스트가 만든 것만 골라 id 를 돌려준다. */
    private List<UUID> claimOwn(Instant now, UUID... ownIds) {
        Set<UUID> own = Set.of(ownIds);
        return outboxRepository.claimPending(10_000, now).stream()
            .map(CompletionConfirmedOutboxEvent::id)
            .filter(own::contains)
            .toList();
    }

    private UUID insertOutbox(
        UUID completionId,
        UUID enrollmentId,
        UUID courseId,
        String eventType,
        String status,
        Instant nextRetryAt
    ) {
        UUID eventId = UUID.randomUUID();
        String payload = "{\"completionId\":\"" + completionId
            + "\",\"enrollmentId\":\"" + enrollmentId
            + "\",\"courseId\":\"" + courseId + "\"}";
        jdbcTemplate.update("""
            INSERT INTO tb_outbox_events (
                id, aggregate_type, aggregate_id, event_type, payload, status,
                occurred_at, retry_count, next_retry_at, created_at
            ) VALUES (?, 'COMPLETION', ?, ?, CAST(? AS JSONB), ?, ?, 0, ?, ?)
            """, eventId, completionId, eventType, payload, status, offset(NOW),
            nextRetryAt == null ? null : offset(nextRetryAt), offset(NOW));
        return eventId;
    }

    private int countGroups(UUID completionId) {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM tb_credential_groups WHERE completion_id = ?",
            Integer.class, completionId);
    }

    private String statusOf(UUID eventId) {
        return jdbcTemplate.queryForObject(
            "SELECT status FROM tb_outbox_events WHERE id = ?", String.class, eventId);
    }

    private int retryCountOf(UUID eventId) {
        return jdbcTemplate.queryForObject(
            "SELECT retry_count FROM tb_outbox_events WHERE id = ?", Integer.class, eventId);
    }

    private OffsetDateTime nextRetryAtOf(UUID eventId) {
        return jdbcTemplate.queryForObject(
            "SELECT next_retry_at FROM tb_outbox_events WHERE id = ?",
            OffsetDateTime.class, eventId);
    }

    private String errorCodeOf(UUID eventId) {
        return jdbcTemplate.queryForObject(
            "SELECT error_message FROM tb_outbox_events WHERE id = ?", String.class, eventId);
    }

    private OffsetDateTime offset(Instant instant) {
        return OffsetDateTime.ofInstant(instant, java.time.ZoneOffset.UTC);
    }

    private record Fixture(UUID completionId, UUID enrollmentId, UUID courseId) {
    }
}
