package com.adn.dabaeum.credential;

import static org.assertj.core.api.Assertions.assertThat;

import com.adn.dabaeum.common.security.AuthenticatedRole;
import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import com.adn.dabaeum.credential.application.CredentialApplicationService;
import com.adn.dabaeum.credential.application.ReissueCredentialCommand;
import com.adn.dabaeum.credential.application.RevokeCredentialCommand;
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

class CredentialLifecycleIntegrationTest extends RemotePostgresIntegrationTestSupport {

    private static final Instant NOW = Instant.parse("2026-08-07T00:00:00Z");
    private static final String VC_HASH = "a".repeat(64);

    @Autowired
    CredentialApplicationService service;

    @Test
    void recordsIdempotentRevokeWithoutChangingCredentialBeforeFabricCommit() {
        Fixture fixture = insertIssuedFixture("revoke");
        AuthenticatedUserContext actor = institutionAdmin(fixture.institutionId());
        RevokeCredentialCommand command = new RevokeCredentialCommand(
            fixture.credentialId(), "duplicate completion", "revoke-" + UUID.randomUUID(), actor, NOW);

        Credential first = service.revoke(command);
        Credential repeated = service.revoke(command);

        assertThat(first.id()).isEqualTo(fixture.credentialId());
        assertThat(first.status()).isEqualTo(CredentialStatus.ISSUED);
        assertThat(repeated.id()).isEqualTo(first.id());
        assertThat(jdbcTemplate.queryForObject(
            "SELECT status FROM tb_credentials WHERE id = ?", String.class, fixture.credentialId()))
            .isEqualTo("ISSUED");
        assertThat(jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
              FROM tb_blockchain_transactions
             WHERE reference_id = ?
               AND transaction_type = 'VC_REVOKE'
               AND status = 'PENDING'
               AND operation_reason = ?
            """, Integer.class, fixture.credentialId(), "duplicate completion")).isEqualTo(1);
    }

    @Test
    void recordsReissueAsVersionedPendingCredentialWhilePreviousRemainsIssued() {
        Fixture fixture = insertIssuedFixture("reissue");
        AuthenticatedUserContext actor = institutionAdmin(fixture.institutionId());
        ReissueCredentialCommand command = new ReissueCredentialCommand(
            fixture.credentialId(), "corrected completion", NOW.plusSeconds(86_400),
            "reissue-" + UUID.randomUUID(), actor, NOW);

        Credential replacement = service.reissue(command);

        assertThat(replacement.status()).isEqualTo(CredentialStatus.PENDING);
        assertThat(replacement.previousCredentialId()).isEqualTo(fixture.credentialId());
        assertThat(replacement.versionNo()).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
            "SELECT status FROM tb_credentials WHERE id = ?", String.class, fixture.credentialId()))
            .isEqualTo("ISSUED");
        assertThat(jdbcTemplate.queryForObject(
            "SELECT status FROM tb_credentials WHERE id = ?", String.class, replacement.id()))
            .isEqualTo("PENDING");
        assertThat(jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
              FROM tb_blockchain_transactions
             WHERE reference_id = ?
               AND transaction_type = 'VC_REISSUE'
               AND status = 'PENDING'
               AND operation_reason = ?
            """, Integer.class, replacement.id(), "corrected completion")).isEqualTo(1);
    }

    private AuthenticatedUserContext institutionAdmin(UUID institutionId) {
        return new AuthenticatedUserContext(
            UUID.randomUUID(), "LOCAL",
            Set.of(new AuthenticatedRole("INSTITUTION_ADMIN", institutionId)));
    }

    private Fixture insertIssuedFixture(String prefix) {
        UUID institutionId = UUID.randomUUID();
        jdbcTemplate.update(
            "INSERT INTO tb_institutions (id, institution_code, name) VALUES (?, ?, ?)",
            institutionId, uniqueCode("inst"), uniqueCode("institution-name"));
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
            """, courseId, institutionId, uniqueCode("course"),
            uniqueCode("course-title"), LocalDate.of(2026, 8, 1),
            LocalDate.of(2026, 8, 31));
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
        UUID groupId = UUID.randomUUID();
        jdbcTemplate.update(
            "INSERT INTO tb_credential_groups (id, completion_id, created_at) VALUES (?, ?, ?)",
            groupId, completionId, offset(NOW));
        UUID credentialId = UUID.randomUUID();
        jdbcTemplate.update("""
            INSERT INTO tb_credentials (
                id, credential_group_id, credential_no, version_no,
                issuer_identifier, subject_identifier, credential_type, status,
                valid_from, valid_until, vc_payload, vc_hash, issued_at,
                created_at, updated_at
            ) VALUES (?, ?, ?, 1, ?, ?, 'LIFELONG_EDUCATION_COMPLETION', 'ISSUED',
                      ?, ?, ?, ?, ?, ?, ?)
            """, credentialId, groupId, "CERT-" + UUID.randomUUID(),
            "urn:dabaeum:issuer:" + institutionId, "urn:dabaeum:subject:" + userId,
            offset(NOW), offset(NOW.plusSeconds(86_400)), "{\"credentialSubject\":{}}", VC_HASH,
            offset(NOW), offset(NOW), offset(NOW));
        return new Fixture(institutionId, credentialId);
    }

    private OffsetDateTime offset(Instant instant) {
        return OffsetDateTime.ofInstant(instant, java.time.ZoneOffset.UTC);
    }

    private record Fixture(UUID institutionId, UUID credentialId) {
    }
}
