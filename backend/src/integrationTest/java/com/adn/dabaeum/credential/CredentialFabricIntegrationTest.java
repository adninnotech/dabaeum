package com.adn.dabaeum.credential;

import static org.assertj.core.api.Assertions.assertThat;

import com.adn.dabaeum.credential.domain.CredentialVerification;
import com.adn.dabaeum.credential.domain.CredentialVerificationRepository;
import com.adn.dabaeum.credential.domain.CredentialVerificationResult;
import com.adn.dabaeum.support.RemotePostgresIntegrationTestSupport;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 원격 인수 검증 경계이다. 세부 생명주기 픽스처는 Task 5/7/8/9 전용 통합 테스트에
 * 유지하며, 이 테스트는 Credential·Fabric 통합 소스 세트에서 공통 DB Gate와 감사 롤백 계약을
 * 검증한다.
 */
class CredentialFabricIntegrationTest extends RemotePostgresIntegrationTestSupport {

    @Autowired
    CredentialVerificationRepository verifications;

    @Test
    void remoteCredentialSchemaAndVerificationAuditAreAvailableAndRollbackScoped() {
        assertThat(flyway().info().current().getVersion().toString()).isEqualTo("19");
        assertThat(jdbcTemplate.queryForObject("""
            SELECT COUNT(*) FROM information_schema.columns
             WHERE table_schema = 'public'
               AND table_name = 'tb_credential_verifications'
               AND column_name IN ('presented_credential_no', 'presented_hash', 'result', 'metadata')
            """, Integer.class)).isEqualTo(4);
        assertThat(jdbcTemplate.queryForObject("""
            SELECT COUNT(*) FROM information_schema.columns
             WHERE table_schema = 'public'
               AND table_name = 'tb_blockchain_transactions'
               AND column_name = 'operation_reason'
            """, Integer.class)).isEqualTo(1);

        UUID verificationId = UUID.randomUUID();
        verifications.insert(new CredentialVerification(
            verificationId, null, "CREDENTIAL-NOT-FOUND", null, "API", "INDIVIDUAL", null,
            CredentialVerificationResult.NOT_FOUND, Instant.parse("2026-08-07T00:00:00Z"),
            null, null, "{}", Instant.parse("2026-08-07T00:00:00Z")));
        assertThat(jdbcTemplate.queryForObject(
            "SELECT result FROM tb_credential_verifications WHERE id = ?",
            String.class, verificationId)).isEqualTo("NOT_FOUND");
    }
}
