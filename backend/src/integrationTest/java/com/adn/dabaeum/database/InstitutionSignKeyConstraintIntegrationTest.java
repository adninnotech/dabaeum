package com.adn.dabaeum.database;

import static org.assertj.core.api.Assertions.assertThat;

import com.adn.dabaeum.support.RemotePostgresIntegrationTestSupport;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * V22 의 제약과 트리거를 실제 PostgreSQL 로 확인한다 (설계서 §11.1, §11.2).
 *
 * <p>제약 위반 검증은 한 테스트에 하나씩만 둔다. PostgreSQL 은 실패한 문장 하나로 트랜잭션 전체를
 * 중단시키므로(25P02) 같은 트랜잭션에서 두 번째 위반을 확인할 수 없다.
 */
class InstitutionSignKeyConstraintIntegrationTest extends RemotePostgresIntegrationTestSupport {

    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-09-10T00:00:00Z");

    @Test
    void createsTheFourTablesAndTheNewColumns() {
        assertThat(jdbcTemplate.queryForObject("""
            SELECT COUNT(*) FROM information_schema.tables
             WHERE table_schema = 'public'
               AND table_name IN ('tb_institution_sign_keys', 'tb_institution_sign_key_versions',
                                  'tb_kms_operations', 'tb_kms_approvals')
            """, Integer.class)).isEqualTo(4);
        assertThat(jdbcTemplate.queryForObject("""
            SELECT COUNT(*) FROM information_schema.columns
             WHERE table_name = 'tb_credentials'
               AND column_name IN ('sign_key_id', 'sign_key_version', 'vc_jws_header',
                                   'vc_jws_payload', 'sign_attempt_no')
            """, Integer.class)).isEqualTo(5);
        assertThat(jdbcTemplate.queryForObject("""
            SELECT COUNT(*) FROM information_schema.columns
             WHERE table_name = 'tb_credential_status_lists'
               AND column_name IN ('sign_key_id', 'sign_key_version')
            """, Integer.class)).isEqualTo(2);
    }

    @Test
    void onlyOneCurrentMasterPerInstitutionAndPurpose() {
        UUID institutionId = insertInstitution();
        insertMaster(institutionId, "CREDENTIAL", 1, true, "ACTIVE");

        assertSqlState("23505", () -> insertMaster(institutionId, "CREDENTIAL", 2, true, "ACTIVE"));
    }

    @Test
    void aSecondGenerationCanCoexistWhenItIsNotCurrent() {
        UUID institutionId = insertInstitution();
        insertMaster(institutionId, "CREDENTIAL", 1, false, "COMPROMISED");
        insertMaster(institutionId, "CREDENTIAL", 2, true, "ACTIVE");

        assertThat(jdbcTemplate.queryForObject("""
            SELECT COUNT(*) FROM tb_institution_sign_keys WHERE institution_id = ?
            """, Integer.class, institutionId)).isEqualTo(2);
    }

    @Test
    void statusListKeysCannotBeSuspended() {
        assertSqlState("23514", () ->
            insertMaster(insertInstitution(), "STATUS_LIST", 1, true, "SUSPENDED"));
    }

    @Test
    void aCompromisedMasterCannotBeCurrent() {
        assertSqlState("23514", () ->
            insertMaster(insertInstitution(), "CREDENTIAL", 1, true, "COMPROMISED"));
    }

    @Test
    void aRevokedMasterNeedsRevokedAtAndReason() {
        UUID institutionId = insertInstitution();

        assertSqlState("23514", () -> jdbcTemplate.update("""
            INSERT INTO tb_institution_sign_keys (
                id, institution_id, purpose, generation, is_current, mount, key_name, key_type,
                current_version, status, version_no, created_at, updated_at
            ) VALUES (?, ?, 'CREDENTIAL', 1, true, 'education-sign', ?, 'ed25519', 1, 'REVOKED', 1, ?, ?)
            """, UUID.randomUUID(), institutionId, "institution-" + institutionId, NOW, NOW));
    }

    @Test
    void kidFragmentIsUniqueWithinAnInstitutionAcrossGenerations() {
        UUID institutionId = insertInstitution();
        UUID first = insertMaster(institutionId, "CREDENTIAL", 1, false, "COMPROMISED");
        UUID second = insertMaster(institutionId, "CREDENTIAL", 2, true, "ACTIVE");
        insertVersion(first, institutionId, 1, "v1", "COMPROMISED");

        assertSqlState("23505", () -> insertVersion(second, institutionId, 1, "v1", "ACTIVE"));
    }

    @Test
    void aVersionCannotPointAtAMasterOfAnotherInstitution() {
        UUID owner = insertInstitution();
        UUID other = insertInstitution();
        UUID master = insertMaster(owner, "CREDENTIAL", 1, true, "ACTIVE");

        assertSqlState("23503", () -> insertVersion(master, other, 1, "v1", "ACTIVE"));
    }

    @Test
    void aCompromisedVersionKeepsItsStatusOnUpdate() {
        UUID institutionId = insertInstitution();
        UUID master = insertMaster(institutionId, "CREDENTIAL", 1, true, "ACTIVE");
        UUID version = insertVersion(master, institutionId, 1, "v1", "COMPROMISED");

        int updated = jdbcTemplate.update("""
            UPDATE tb_institution_sign_key_versions
               SET status = 'RETIRED', retired_at = ?
             WHERE id = ?
            """, NOW, version);

        assertThat(updated).isZero();
        assertThat(jdbcTemplate.queryForObject(
            "SELECT status FROM tb_institution_sign_key_versions WHERE id = ?",
            String.class, version)).isEqualTo("COMPROMISED");
    }

    @Test
    void onlyOneInProgressOperationPerKey() {
        UUID institutionId = insertInstitution();
        UUID master = insertMaster(institutionId, "CREDENTIAL", 1, true, "ACTIVE");
        insertOperation(master, institutionId, "PREPARED");
        insertOperation(master, institutionId, "COMPLETED");

        assertSqlState("23505", () -> insertOperation(master, institutionId, "UNCONFIRMED"));
    }

    private UUID insertInstitution() {
        UUID institutionId = UUID.randomUUID();
        jdbcTemplate.update(
            "INSERT INTO tb_institutions (id, institution_code, name) VALUES (?, ?, ?)",
            institutionId, uniqueCode("institution"), uniqueCode("institution-name"));
        return institutionId;
    }

    private UUID insertMaster(
        UUID institutionId, String purpose, int generation, boolean current, String status
    ) {
        UUID id = UUID.randomUUID();
        String keyName = "institution-" + institutionId + (generation == 1 ? "" : "-g" + generation);
        OffsetDateTime compromisedAt = "COMPROMISED".equals(status) ? NOW : null;
        OffsetDateTime suspendedAt = "SUSPENDED".equals(status) ? NOW : null;
        jdbcTemplate.update("""
            INSERT INTO tb_institution_sign_keys (
                id, institution_id, purpose, generation, is_current, mount, key_name, key_type,
                current_version, status, status_reason, suspended_at, compromised_at,
                version_no, created_at, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, 'ed25519', 1, ?, ?, ?, ?, 1, ?, ?)
            """, id, institutionId, purpose, generation, current,
            "CREDENTIAL".equals(purpose) ? "education-sign" : "education-status", keyName,
            status, "COMPROMISED".equals(status) ? "test" : null, suspendedAt, compromisedAt,
            NOW, NOW);
        return id;
    }

    private UUID insertVersion(
        UUID signKeyId, UUID institutionId, int keyVersion, String fragment, String status
    ) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
            INSERT INTO tb_institution_sign_key_versions (
                id, sign_key_id, institution_id, key_version, kid_fragment, public_key_jwk, source,
                status, activated_at, compromised_at, created_at
            ) VALUES (?, ?, ?, ?, ?, '{"kty":"OKP","crv":"Ed25519","alg":"EdDSA","use":"sig","x":"AA"}'::jsonb,
                      'OPENBAO', ?, ?, ?, ?)
            """, id, signKeyId, institutionId, keyVersion, fragment, status, NOW,
            "COMPROMISED".equals(status) ? NOW : null, NOW);
        return id;
    }

    private void insertOperation(UUID signKeyId, UUID institutionId, String status) {
        jdbcTemplate.update("""
            INSERT INTO tb_kms_operations (
                id, sign_key_id, institution_id, purpose, action, status, prepared_at,
                idempotency_key, created_at, updated_at
            ) VALUES (?, ?, ?, 'CREDENTIAL', 'ROTATE', ?, ?, ?, ?, ?)
            """, UUID.randomUUID(), signKeyId, institutionId, status, NOW,
            uniqueCode("idem"), NOW, NOW);
    }
}
