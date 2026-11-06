package com.adn.dabaeum.accesslog;

import static org.assertj.core.api.Assertions.assertThat;

import com.adn.dabaeum.accesslog.domain.AccessLogAction;
import com.adn.dabaeum.accesslog.domain.AdminAccessLog;
import com.adn.dabaeum.accesslog.domain.AdminAccessLogCriteria;
import com.adn.dabaeum.accesslog.domain.AdminAccessLogPage;
import com.adn.dabaeum.accesslog.domain.AdminAccessLogRepository;
import com.adn.dabaeum.support.RemotePostgresIntegrationTestSupport;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** 설계서 §10.2 — 접속기록 저장(uuid[]·inet)과 보존 기간 삭제. */
class AdminAccessLogPersistenceIntegrationTest extends RemotePostgresIntegrationTestSupport {

    @Autowired
    AdminAccessLogRepository repository;

    @Test
    void savesTargetsIpAndRequestId() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        AdminAccessLog log = log(List.of(a, b), "203.0.113.7", requestId, Instant.now());

        repository.save(log);

        assertThat(jdbcTemplate.queryForObject(
            "SELECT cardinality(target_user_ids) FROM tb_admin_access_logs WHERE id = ?", Integer.class, log.id()))
            .isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
            "SELECT ? = ANY(target_user_ids) FROM tb_admin_access_logs WHERE id = ?", Boolean.class, a, log.id()))
            .isTrue();
        assertThat(jdbcTemplate.queryForObject(
            "SELECT host(ip_address) FROM tb_admin_access_logs WHERE id = ?", String.class, log.id()))
            .isEqualTo("203.0.113.7");
        assertThat(jdbcTemplate.queryForObject(
            "SELECT request_id FROM tb_admin_access_logs WHERE id = ?", UUID.class, log.id()))
            .isEqualTo(requestId);
    }

    @Test
    void savesCountOnlyRowWithoutTargetsOrIp() {
        AdminAccessLog log = log(null, null, null, Instant.now());

        repository.save(log);

        assertThat(jdbcTemplate.queryForObject(
            "SELECT target_user_ids IS NULL AND ip_address IS NULL FROM tb_admin_access_logs WHERE id = ?",
            Boolean.class, log.id())).isTrue();
    }

    @Test
    void deletesOnlyRowsCreatedBeforeThreshold() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        AdminAccessLog old = log(null, null, null, now.minus(3 * 365, ChronoUnit.DAYS));
        AdminAccessLog recent = log(null, null, null, now.minus(1, ChronoUnit.DAYS));
        repository.save(old);
        repository.save(recent);

        // dev DB 의 다른 오래된 기록도 지워질 수 있어 개수는 단정하지 않는다(롤백으로 원복).
        assertThat(repository.deleteCreatedBefore(now.minus(2 * 365, ChronoUnit.DAYS))).isPositive();

        assertThat(count(old.id())).isZero();
        assertThat(count(recent.id())).isEqualTo(1);
    }

    @Test
    void searchFiltersByActorActionAndPeriodNewestFirst() {
        UUID actor = UUID.randomUUID();
        UUID target = UUID.randomUUID();
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        AdminAccessLog older = withActor(log(List.of(target), "203.0.113.7", null, now.minus(2, ChronoUnit.HOURS)), actor, AccessLogAction.READ);
        AdminAccessLog newer = withActor(log(null, null, null, now.minus(1, ChronoUnit.HOURS)), actor, AccessLogAction.READ);
        AdminAccessLog update = withActor(log(null, null, null, now.minus(30, ChronoUnit.MINUTES)), actor, AccessLogAction.UPDATE);
        AdminAccessLog outside = withActor(log(null, null, null, now.minus(10, ChronoUnit.DAYS)), actor, AccessLogAction.READ);
        repository.save(older);
        repository.save(newer);
        repository.save(update);
        repository.save(outside);

        AdminAccessLogPage page = repository.search(new AdminAccessLogCriteria(
            actor, AccessLogAction.READ, now.minus(1, ChronoUnit.DAYS), now, 0, 10));

        assertThat(page.totalElements()).isEqualTo(2);
        assertThat(page.entries()).extracting(entry -> entry.log().id()).containsExactly(newer.id(), older.id());
        AdminAccessLog read = page.entries().get(1).log();
        assertThat(read.targetUserIds()).containsExactly(target);
        assertThat(read.ipAddress()).isEqualTo("203.0.113.7");
        assertThat(page.entries().get(0).log().targetUserIds()).isNull();
    }

    private static AdminAccessLog withActor(AdminAccessLog log, UUID actor, AccessLogAction action) {
        return new AdminAccessLog(log.id(), actor, log.actorRole(), action, log.endpoint(), log.targetUserIds(),
            log.resultCount(), log.statusCode(), log.ipAddress(), log.userAgent(), log.requestId(), log.createdAt());
    }

    private int count(UUID id) {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM tb_admin_access_logs WHERE id = ?", Integer.class, id);
    }

    private static AdminAccessLog log(List<UUID> targets, String ip, UUID requestId, Instant at) {
        return new AdminAccessLog(
            UUID.randomUUID(), UUID.randomUUID(), "PLATFORM_ADMIN", AccessLogAction.READ,
            "GET /api/v1/users/{userId}", targets, targets == null ? 3 : targets.size(), 200,
            ip, "junit", requestId, at);
    }
}
