package com.adn.dabaeum.credential;

import static org.assertj.core.api.Assertions.assertThat;

import com.adn.dabaeum.credential.domain.Credential;
import com.adn.dabaeum.credential.domain.CredentialGroup;
import com.adn.dabaeum.credential.domain.CredentialGroupRepository;
import com.adn.dabaeum.credential.domain.CredentialRepository;
import com.adn.dabaeum.credential.domain.CredentialStatus;
import com.adn.dabaeum.credential.domain.CredentialStatusList;
import com.adn.dabaeum.credential.domain.CredentialStatusListRepository;
import com.adn.dabaeum.credential.domain.LegacySignKeyBackfillRepository;
import com.adn.dabaeum.support.RemotePostgresIntegrationTestSupport;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** §11.3 백필 SQL 의 조인 경로와 봉투 파싱을 실제 PostgreSQL 로 확인한다. */
class LegacySignKeyBackfillIntegrationTest extends RemotePostgresIntegrationTestSupport {

    private static final Instant NOW = Instant.parse("2026-09-10T00:00:00Z");
    private static final String VC_HASH = "c".repeat(64);
    private static final String ENVELOPE =
        "{\"mediaType\":\"application/vc+jwt\",\"compactJws\":\"eyJhbGc.eyJ2YyI.c2ln\"}";

    @Autowired CredentialGroupRepository groupRepository;
    @Autowired CredentialRepository credentialRepository;
    @Autowired CredentialStatusListRepository statusListRepository;
    @Autowired LegacySignKeyBackfillRepository backfill;

    @Test
    void findsInstitutionsThroughTheCourseAndLinksOnlyTheirIssuedRows() {
        UUID institutionId = insertInstitution();
        UUID other = insertInstitution();
        Credential issued = insertCredential(institutionId, CredentialStatus.ISSUED, ENVELOPE);
        Credential pending = insertCredential(institutionId, CredentialStatus.PENDING, null);
        Credential otherIssued = insertCredential(other, CredentialStatus.ISSUED, ENVELOPE);
        CredentialStatusList list = insertList(institutionId);
        UUID signKeyId = insertProvisioningMaster(institutionId);

        assertThat(backfill.findInstitutionsWithUnlinkedRows()).contains(institutionId, other);

        assertThat(backfill.linkCredentials(institutionId, signKeyId)).isEqualTo(1);
        assertThat(backfill.linkStatusLists(institutionId, signKeyId)).isEqualTo(1);

        Map<String, Object> linked = jdbcTemplate.queryForMap("""
            SELECT sign_key_id, sign_key_version, sign_attempt_no, vc_jws_header, vc_jws_payload
              FROM tb_credentials WHERE id = ?
            """, issued.id());
        assertThat(linked).containsEntry("sign_key_id", signKeyId)
            .containsEntry("sign_key_version", 0)
            .containsEntry("sign_attempt_no", 1)
            .containsEntry("vc_jws_header", "eyJhbGc")
            .containsEntry("vc_jws_payload", "eyJ2YyI");
        assertThat(jdbcTemplate.queryForObject(
            "SELECT sign_key_id FROM tb_credentials WHERE id = ?", UUID.class, pending.id())).isNull();
        assertThat(jdbcTemplate.queryForObject(
            "SELECT sign_key_id FROM tb_credentials WHERE id = ?", UUID.class, otherIssued.id())).isNull();
        assertThat(jdbcTemplate.queryForObject(
            "SELECT sign_key_version FROM tb_credential_status_lists WHERE id = ?",
            Integer.class, list.id())).isZero();

        // 두 번째 실행은 0행이고 기관은 대상에서 빠진다.
        assertThat(backfill.linkCredentials(institutionId, signKeyId)).isZero();
        assertThat(backfill.linkStatusLists(institutionId, signKeyId)).isZero();
        assertThat(backfill.findInstitutionsWithUnlinkedRows()).doesNotContain(institutionId);
    }

    @Test
    void aNonEnvelopePayloadIsLinkedWithoutJwsSegments() {
        UUID institutionId = insertInstitution();
        Credential legacy = insertCredential(institutionId, CredentialStatus.ISSUED, "header.payload.signature");
        UUID signKeyId = insertProvisioningMaster(institutionId);

        assertThat(backfill.linkCredentials(institutionId, signKeyId)).isEqualTo(1);

        Map<String, Object> linked = jdbcTemplate.queryForMap(
            "SELECT sign_key_version, vc_jws_header FROM tb_credentials WHERE id = ?", legacy.id());
        assertThat(linked).containsEntry("sign_key_version", 0).containsEntry("vc_jws_header", null);
    }

    private UUID insertProvisioningMaster(UUID institutionId) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
            INSERT INTO tb_institution_sign_keys (
                id, institution_id, purpose, generation, is_current, mount, key_name, key_type,
                current_version, status, version_no, created_at, updated_at
            ) VALUES (?, ?, 'CREDENTIAL', 1, true, 'education-sign', ?, 'ed25519', 0, 'PROVISIONING', 1, ?, ?)
            """, id, institutionId, "institution-" + institutionId,
            OffsetDateTime.parse("2026-09-10T00:00:00Z"), OffsetDateTime.parse("2026-09-10T00:00:00Z"));
        return id;
    }

    private CredentialStatusList insertList(UUID institutionId) {
        CredentialStatusList list = new CredentialStatusList(UUID.randomUUID(), institutionId, 1,
            CredentialStatusList.REVOCATION, CredentialStatusList.MINIMUM_CAPACITY, NOW);
        statusListRepository.insert(list);
        return list;
    }

    private Credential insertCredential(UUID institutionId, CredentialStatus status, String payload) {
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
            """, enrollmentId, courseId, userId, OffsetDateTime.now());
        UUID completionId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO tb_completions (id, enrollment_id) VALUES (?, ?)",
            completionId, enrollmentId);
        CredentialGroup group = new CredentialGroup(UUID.randomUUID(), completionId, NOW);
        groupRepository.insert(group);
        boolean issued = status != CredentialStatus.PENDING;
        Credential credential = new Credential(
            UUID.randomUUID(), group.id(), null, uniqueCode("credential"), 1,
            issued ? "urn:dabaeum:institution:" + institutionId : null,
            issued ? "urn:dabaeum:user:" + userId : null,
            "LIFELONG_EDUCATION_COMPLETION", status, issued ? NOW : null, null,
            payload, issued ? VC_HASH : null, null, null,
            issued ? NOW : null, null, null, null, null, NOW, NOW);
        credentialRepository.insert(credential);
        return credential;
    }

    private UUID insertInstitution() {
        UUID institutionId = UUID.randomUUID();
        jdbcTemplate.update(
            "INSERT INTO tb_institutions (id, institution_code, name) VALUES (?, ?, ?)",
            institutionId, uniqueCode("institution"), uniqueCode("institution-name"));
        return institutionId;
    }
}
