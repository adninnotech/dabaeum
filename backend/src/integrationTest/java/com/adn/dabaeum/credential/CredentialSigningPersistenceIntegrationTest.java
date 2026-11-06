package com.adn.dabaeum.credential;

import static org.assertj.core.api.Assertions.assertThat;

import com.adn.dabaeum.credential.domain.Credential;
import com.adn.dabaeum.credential.domain.CredentialGroup;
import com.adn.dabaeum.credential.domain.CredentialGroupRepository;
import com.adn.dabaeum.credential.domain.CredentialRepository;
import com.adn.dabaeum.credential.domain.CredentialSigningInput;
import com.adn.dabaeum.credential.domain.CredentialSigningRepository;
import com.adn.dabaeum.credential.domain.CredentialSigningState;
import com.adn.dabaeum.credential.domain.CredentialStatus;
import com.adn.dabaeum.credential.domain.InstitutionSignKey;
import com.adn.dabaeum.credential.domain.InstitutionSignKeyRepository;
import com.adn.dabaeum.credential.domain.InstitutionSignKeyVersion;
import com.adn.dabaeum.credential.domain.InstitutionSignKeyVersionRepository;
import com.adn.dabaeum.credential.domain.SignKeyPurpose;
import com.adn.dabaeum.credential.domain.SignKeyVersionStatus;
import com.adn.dabaeum.support.RemotePostgresIntegrationTestSupport;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** 설계서 §8.1 5·8단계의 조건부 쓰기와 10단계 FOR SHARE 조회를 실제 PostgreSQL 로 확인한다. */
class CredentialSigningPersistenceIntegrationTest extends RemotePostgresIntegrationTestSupport {

    private static final Instant NOW = Instant.parse("2026-09-10T00:00:00Z");
    private static final String JWK =
        "{\"kty\":\"OKP\",\"crv\":\"Ed25519\",\"alg\":\"EdDSA\",\"use\":\"sig\",\"x\":\"AA\"}";
    private static final CredentialSigningInput V1 = new CredentialSigningInput("djEtaGVhZGVy", "cGF5bG9hZA");
    private static final CredentialSigningInput V2 = new CredentialSigningInput("djItaGVhZGVy", "cGF5bG9hZA");
    private static final CredentialSigningInput OTHER_PAYLOAD = new CredentialSigningInput("djItaGVhZGVy", "b3RoZXI");
    private static final String ENVELOPE =
        "{\"mediaType\":\"application/vc+jwt\",\"compactJws\":\"djItaGVhZGVy.cGF5bG9hZA.c2ln\"}";
    private static final String HASH = "d".repeat(64);

    @Autowired CredentialGroupRepository groupRepository;
    @Autowired CredentialRepository credentialRepository;
    @Autowired InstitutionSignKeyRepository keys;
    @Autowired InstitutionSignKeyVersionRepository versions;
    @Autowired CredentialSigningRepository signing;

    @Test
    void findsTheInstitutionThroughTheCourse() {
        UUID institutionId = insertInstitution();
        Credential credential = insertCredential(institutionId, CredentialStatus.ISSUING);

        assertThat(signing.findInstitutionId(credential.id())).contains(institutionId);
        assertThat(signing.findInstitutionId(UUID.randomUUID())).isEmpty();
    }

    @Test
    void commitsTheFirstInputOnceAndFixesTheIssuanceFields() {
        UUID institutionId = insertInstitution();
        InstitutionSignKey key = insertActiveKey(institutionId);
        Credential credential = insertCredential(institutionId, CredentialStatus.ISSUING);

        assertThat(signing.commitSigningInput(credential.id(), key.id(), 1, V1,
            "urn:issuer", "urn:subject", NOW, NOW)).isTrue();
        // 늦게 온 두 번째 워커는 다른 입력으로 덮어쓰지 못한다.
        assertThat(signing.commitSigningInput(credential.id(), key.id(), 2, V2,
            "urn:issuer", "urn:subject", NOW.plusSeconds(5), NOW.plusSeconds(5))).isFalse();

        CredentialSigningState state = signing.findSigningState(credential.id()).orElseThrow();
        assertThat(state.inputCommitted()).isTrue();
        assertThat(state.input()).isEqualTo(V1);
        assertThat(state.signKeyId()).isEqualTo(key.id());
        assertThat(state.signKeyVersion()).isEqualTo(1);
        assertThat(state.signAttemptNo()).isEqualTo(1);
        Map<String, Object> fixed = jdbcTemplate.queryForMap(
            "SELECT issuer_identifier, subject_identifier FROM tb_credentials WHERE id = ?", credential.id());
        assertThat(fixed).containsEntry("issuer_identifier", "urn:issuer")
            .containsEntry("subject_identifier", "urn:subject");
        assertThat(credentialRepository.findById(credential.id()).orElseThrow().issuedAt()).isEqualTo(NOW);
    }

    @Test
    void doesNotCommitOnARowThatIsNotIssuing() {
        UUID institutionId = insertInstitution();
        InstitutionSignKey key = insertActiveKey(institutionId);
        Credential pending = insertCredential(institutionId, CredentialStatus.PENDING);

        assertThat(signing.commitSigningInput(pending.id(), key.id(), 1, V1,
            "urn:issuer", "urn:subject", NOW, NOW)).isFalse();
        assertThat(signing.findSigningState(pending.id()).orElseThrow().inputCommitted()).isFalse();
    }

    @Test
    void recommitRaisesTheAttemptAndKeepsThePayload() {
        UUID institutionId = insertInstitution();
        InstitutionSignKey key = insertActiveKey(institutionId);
        Credential credential = insertCredential(institutionId, CredentialStatus.ISSUING);
        signing.commitSigningInput(credential.id(), key.id(), 1, V1, "urn:issuer", "urn:subject", NOW, NOW);

        assertThat(signing.recommitHeader(credential.id(), 1, key.id(), 2, V2, NOW.plusSeconds(1))).isTrue();
        assertThat(signing.recommitHeader(credential.id(), 1, key.id(), 3, V2, NOW.plusSeconds(2))).isFalse();
        assertThat(signing.recommitHeader(credential.id(), 2, key.id(), 3, OTHER_PAYLOAD, NOW.plusSeconds(2)))
            .isFalse();

        CredentialSigningState state = signing.findSigningState(credential.id()).orElseThrow();
        assertThat(state.signAttemptNo()).isEqualTo(2);
        assertThat(state.signKeyVersion()).isEqualTo(2);
        assertThat(state.input()).isEqualTo(V2);
    }

    @Test
    void storesTheSignatureOnlyForTheCurrentAttempt() {
        UUID institutionId = insertInstitution();
        InstitutionSignKey key = insertActiveKey(institutionId);
        Credential credential = insertCredential(institutionId, CredentialStatus.ISSUING);
        signing.commitSigningInput(credential.id(), key.id(), 1, V1, "urn:issuer", "urn:subject", NOW, NOW);
        signing.recommitHeader(credential.id(), 1, key.id(), 2, V2, NOW.plusSeconds(1));

        // v2 시도가 시작된 뒤 도착한 v1 응답은 버린다.
        assertThat(signing.storeSignature(credential.id(), 1, key.id(), 1, V1, ENVELOPE, HASH, NOW)).isFalse();
        assertThat(signing.storeSignature(credential.id(), 2, key.id(), 2, V2, ENVELOPE, HASH, NOW)).isTrue();
        assertThat(signing.storeSignature(credential.id(), 2, key.id(), 2, V2, ENVELOPE, HASH, NOW)).isFalse();

        Credential stored = credentialRepository.findById(credential.id()).orElseThrow();
        assertThat(stored.status()).isEqualTo(CredentialStatus.ISSUING);
        assertThat(stored.vcPayload()).isEqualTo(ENVELOPE);
        assertThat(stored.vcHash()).isEqualTo(HASH);
    }

    @Test
    void readsTheVersionStatusForShare() {
        UUID institutionId = insertInstitution();
        InstitutionSignKey key = insertActiveKey(institutionId);

        assertThat(versions.findStatusForShare(key.id(), 1)).contains(SignKeyVersionStatus.ACTIVE);
        assertThat(versions.findStatusForShare(key.id(), 9)).isEmpty();
    }

    private InstitutionSignKey insertActiveKey(UUID institutionId) {
        InstitutionSignKey key = InstitutionSignKey.provisioning(
            UUID.randomUUID(), institutionId, SignKeyPurpose.CREDENTIAL, 1, NOW).activate(1, NOW);
        keys.insert(key);
        versions.insert(InstitutionSignKeyVersion.active(UUID.randomUUID(), key.id(), institutionId,
            SignKeyPurpose.CREDENTIAL, 1, 1, JWK, NOW, null, NOW));
        return key;
    }

    private Credential insertCredential(UUID institutionId, CredentialStatus status) {
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
        Credential credential = new Credential(
            UUID.randomUUID(), group.id(), null, uniqueCode("credential"), 1, null, null,
            "LIFELONG_EDUCATION_COMPLETION", status, null, null, null, null, null, null,
            null, null, null, null, null, NOW, NOW);
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
