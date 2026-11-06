package com.adn.dabaeum.database;

import static org.assertj.core.api.Assertions.assertThat;

import com.adn.dabaeum.support.RemotePostgresIntegrationTestSupport;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * V40 — 로그인 잠금 컬럼, 비밀번호 변경 시각, 관리자 접속기록 (설계서 §10.2, §10.3).
 * 제약 위반은 한 테스트에 하나씩만 둔다(25P02).
 */
class AdminAccessSecurityConstraintIntegrationTest extends RemotePostgresIntegrationTestSupport {

    @Test
    void newUserStartsWithZeroFailuresAndNoLock() {
        UUID id = insertUser("잠금기본");

        assertThat(jdbcTemplate.queryForObject(
            "SELECT failed_login_count FROM tb_users WHERE id = ?", Integer.class, id)).isZero();
        assertThat(jdbcTemplate.queryForObject(
            "SELECT locked_until FROM tb_users WHERE id = ?", OffsetDateTime.class, id)).isNull();
    }

    @Test
    void failedLoginCountCannotBeNegative() {
        UUID id = insertUser("음수");

        assertSqlState("23514", () -> jdbcTemplate.update(
            "UPDATE tb_users SET failed_login_count = -1 WHERE id = ?", id));
    }

    @Test
    void passwordChangedAtRequiresPasswordHash() {
        UUID userId = insertUser("다대구전용");

        assertSqlState("23514", () -> jdbcTemplate.update("""
            INSERT INTO tb_user_identities (id, user_id, provider, provider_subject, password_changed_at)
            VALUES (?, ?, 'DADAEGU', ?, now())
            """, UUID.randomUUID(), userId, "did-" + userId));
    }

    @Test
    void storesAccessLogWithUuidArrayAndInet() {
        UUID id = insertLog("READ", new UUID[] {UUID.randomUUID(), UUID.randomUUID()});

        assertThat(jdbcTemplate.queryForObject(
            "SELECT cardinality(target_user_ids) FROM tb_admin_access_logs WHERE id = ?", Integer.class, id))
            .isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
            "SELECT host(ip_address) FROM tb_admin_access_logs WHERE id = ?", String.class, id))
            .isEqualTo("203.0.113.7");
    }

    @Test
    void accessLogRejectsUnknownAction() {
        assertSqlState("23514", () -> insertLog("EXPORT", null));
    }

    @Test
    void accessLogRejectsMoreThanThousandTargets() {
        UUID[] targets = new UUID[1001];
        for (int i = 0; i < targets.length; i++) {
            targets[i] = UUID.randomUUID();
        }
        assertSqlState("23514", () -> insertLog("READ", targets));
    }

    @Test
    void accessLogIsAppendOnly() {
        UUID id = insertLog("READ", null);

        assertSqlState("23514", () -> jdbcTemplate.update(
            "UPDATE tb_admin_access_logs SET result_count = 0 WHERE id = ?", id));
    }

    @Test
    void accessLogRowsCanBeDeletedByRetention() {
        UUID id = insertLog("DOWNLOAD", null);

        assertThat(jdbcTemplate.update("DELETE FROM tb_admin_access_logs WHERE id = ?", id)).isEqualTo(1);
    }

    private UUID insertUser(String name) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO tb_users (id, name) VALUES (?, ?)", id, name);
        return id;
    }

    private UUID insertLog(String action, UUID[] targets) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
            INSERT INTO tb_admin_access_logs (
                id, actor_id, actor_role, action, endpoint, target_user_ids,
                result_count, status_code, ip_address, user_agent, request_id, created_at
            ) VALUES (?, ?, 'PLATFORM_ADMIN', ?, 'GET /api/v1/users/{userId}', ?,
                      1, 200, CAST(? AS inet), 'junit', ?, ?)
            """, id, UUID.randomUUID(), action, targets, "203.0.113.7", UUID.randomUUID(),
            OffsetDateTime.now(ZoneOffset.UTC));
        return id;
    }
}
