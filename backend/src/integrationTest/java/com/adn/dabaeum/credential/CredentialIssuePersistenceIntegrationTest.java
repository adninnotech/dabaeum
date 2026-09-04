package com.adn.dabaeum.credential;

import static org.assertj.core.api.Assertions.assertThat;

import com.adn.dabaeum.common.security.AuthenticatedRole;
import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import com.adn.dabaeum.credential.application.CredentialApplicationService;
import com.adn.dabaeum.credential.application.IssueCredentialCommand;
import com.adn.dabaeum.credential.domain.Credential;
import com.adn.dabaeum.credential.domain.CredentialStatus;
import com.adn.dabaeum.support.RemotePostgresIntegrationTestSupport;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class CredentialIssuePersistenceIntegrationTest extends RemotePostgresIntegrationTestSupport {

    private static final Instant NOW = Instant.parse("2026-08-05T00:00:00Z");

    @Autowired
    CredentialApplicationService service;

    @Test
    void persistsPendingCredentialAndFabricAnchorRequestInOneRollbackableTransaction() {
        Fixture fixture = insertCompletedFixture();
        AuthenticatedUserContext actor = new AuthenticatedUserContext(
            UUID.randomUUID(), "LOCAL",
            Set.of(new AuthenticatedRole("INSTITUTION_ADMIN", fixture.institutionId())));
        String idempotencyKey = "k".repeat(128);
        IssueCredentialCommand command = new IssueCredentialCommand(
            fixture.completionId(), NOW.plusSeconds(3600), idempotencyKey, actor, NOW);

        Credential first = service.issue(command);
        Credential repeated = service.issue(command);

        assertThat(first.status()).isEqualTo(CredentialStatus.PENDING);
        assertThat(repeated.id()).isEqualTo(first.id());
        assertThat(jdbcTemplate.queryForObject(
            "SELECT status FROM tb_credentials WHERE id = ?", String.class, first.id()))
            .isEqualTo("PENDING");
        assertThat(jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
              FROM tb_blockchain_transactions
             WHERE reference_id = ?
               AND reference_type = 'CREDENTIAL'
               AND network = 'FABRIC_POC'
               AND transaction_type = 'VC_ANCHOR'
               AND status = 'PENDING'
               AND idempotency_key = ?
            """, Integer.class, first.id(), idempotencyKey)).isEqualTo(1);
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
                start_date, end_date, capacity, status
            ) VALUES (?, ?, ?, ?, 'OFFLINE', ?, ?, 20, 'COMPLETED')
            """, courseId, institutionId, uniqueCode("course"), uniqueCode("course-title"),
            LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));
        UUID enrollmentId = UUID.randomUUID();
        jdbcTemplate.update("""
            INSERT INTO tb_enrollments (id, course_id, user_id, status, approved_at)
            VALUES (?, ?, ?, 'APPROVED', ?)
            """, enrollmentId, courseId, userId, offset(NOW));
        UUID completionId = UUID.randomUUID();
        jdbcTemplate.update("""
            INSERT INTO tb_completions (
                id, enrollment_id, status, attendance_rate, completed_minutes,
                evaluated_at, completed_at, confirmed_by, confirmed_at
            ) VALUES (?, ?, 'COMPLETED', 100.00, 60, ?, ?, ?, ?)
            """, completionId, enrollmentId, offset(NOW), offset(NOW), userId, offset(NOW));
        return new Fixture(institutionId, completionId);
    }

    private OffsetDateTime offset(Instant instant) {
        return OffsetDateTime.ofInstant(instant, java.time.ZoneOffset.UTC);
    }

    private record Fixture(UUID institutionId, UUID completionId) {
    }
}
