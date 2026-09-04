package com.adn.dabaeum.credential;

import static org.assertj.core.api.Assertions.assertThat;

import com.adn.dabaeum.credential.domain.CredentialRepository;
import com.adn.dabaeum.credential.domain.CredentialVerification;
import com.adn.dabaeum.credential.domain.CredentialVerificationRepository;
import com.adn.dabaeum.credential.domain.CredentialVerificationResult;
import com.adn.dabaeum.support.RemotePostgresIntegrationTestSupport;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class CredentialVerificationIntegrationTest extends RemotePostgresIntegrationTestSupport {

    private static final Instant NOW = Instant.parse("2026-08-07T00:00:00Z");

    @Autowired
    CredentialRepository credentials;

    @Autowired
    CredentialVerificationRepository verifications;

    @Test
    void persistsAllResultsAndSupportsCredentialNumberAndHashLookupWithRollback() {
        Fixture fixture = insertIssuedFixture();
        assertThat(credentials.findByCredentialNo(fixture.credentialNo())).isPresent();
        assertThat(credentials.findByCredentialHash(fixture.vcHash())).isPresent();

        CredentialVerification notFound = verification(
            null, "CERT-NOT-FOUND", null, CredentialVerificationResult.NOT_FOUND);
        CredentialVerification valid = verification(
            fixture.credentialId(), fixture.credentialNo(), null, CredentialVerificationResult.VALID);
        CredentialVerification invalid = verification(
            fixture.credentialId(), null, "b".repeat(64), CredentialVerificationResult.INVALID);
        verifications.insert(notFound);
        verifications.insert(valid);
        verifications.insert(invalid);

        assertThat(verifications.countByCredentialId(fixture.credentialId())).isEqualTo(2);
        List<CredentialVerification> page = verifications.findByCredentialId(
            fixture.credentialId(), 10, 0, "verifiedAt,desc");
        assertThat(page).hasSize(2).extracting(CredentialVerification::result)
            .containsExactlyInAnyOrder(CredentialVerificationResult.INVALID,
                CredentialVerificationResult.VALID);
        assertThat(jdbcTemplate.queryForObject("""
            SELECT request_ip IS NULL
              FROM tb_credential_verifications
             WHERE id = ?
            """, Boolean.class, valid.id())).isTrue();
        assertThat(jdbcTemplate.queryForObject("""
            SELECT metadata::text
              FROM tb_credential_verifications
             WHERE id = ?
            """, String.class, valid.id())).isEqualTo("{}");
    }

    private CredentialVerification verification(
        UUID credentialId, String credentialNo, String hash, CredentialVerificationResult result
    ) {
        return new CredentialVerification(UUID.randomUUID(), credentialId, credentialNo, hash,
            "API", "INDIVIDUAL", null, result, NOW, null, null, "{}", NOW);
    }

    private Fixture insertIssuedFixture() {
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
        UUID groupId = UUID.randomUUID();
        jdbcTemplate.update(
            "INSERT INTO tb_credential_groups (id, completion_id, created_at) VALUES (?, ?, ?)",
            groupId, completionId, offset(NOW));
        UUID credentialId = UUID.randomUUID();
        String credentialNo = "CERT-" + UUID.randomUUID();
        String hash = "a".repeat(64);
        jdbcTemplate.update("""
            INSERT INTO tb_credentials (
                id, credential_group_id, credential_no, version_no,
                issuer_identifier, subject_identifier, credential_type, status,
                valid_from, valid_until, vc_payload, vc_hash, issued_at,
                created_at, updated_at
            ) VALUES (?, ?, ?, 1, ?, ?, 'LIFELONG_EDUCATION_COMPLETION', 'ISSUED',
                      ?, ?, ?, ?, ?, ?, ?)
            """, credentialId, groupId, credentialNo,
            "urn:dabaeum:institution:" + institutionId, "urn:dabaeum:user:" + userId,
            offset(NOW), offset(NOW.plusSeconds(86_400)), "{}", hash,
            offset(NOW), offset(NOW), offset(NOW));
        return new Fixture(credentialId, credentialNo, hash);
    }

    private OffsetDateTime offset(Instant instant) {
        return OffsetDateTime.ofInstant(instant, java.time.ZoneOffset.UTC);
    }

    private record Fixture(UUID credentialId, String credentialNo, String vcHash) {
    }
}
