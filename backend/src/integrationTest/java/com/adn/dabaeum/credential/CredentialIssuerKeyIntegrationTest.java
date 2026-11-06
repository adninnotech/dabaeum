package com.adn.dabaeum.credential;

import static org.assertj.core.api.Assertions.assertThat;

import com.adn.dabaeum.credential.application.CredentialIssuerDocumentService;
import com.adn.dabaeum.credential.domain.CredentialPublicJwkFactory;
import com.adn.dabaeum.credential.domain.CredentialVerificationKeyResolver;
import com.adn.dabaeum.credential.domain.CredentialVerificationKeyResolver.DocumentKind;
import com.adn.dabaeum.credential.domain.InstitutionSignKey;
import com.adn.dabaeum.credential.domain.InstitutionSignKeyRepository;
import com.adn.dabaeum.credential.domain.InstitutionSignKeyVersion;
import com.adn.dabaeum.credential.domain.InstitutionSignKeyVersionRepository;
import com.adn.dabaeum.credential.domain.SignKeyPurpose;
import com.adn.dabaeum.support.RemotePostgresIntegrationTestSupport;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** jsonb 로 보존한 JWK 가 발급자 문서와 검증 키로 같은 공개키가 되는지 실제 PostgreSQL 로 확인한다 (§9.2). */
class CredentialIssuerKeyIntegrationTest extends RemotePostgresIntegrationTestSupport {

    private static final Instant NOW = Instant.parse("2026-09-11T00:00:00Z");

    @Autowired InstitutionSignKeyRepository keys;
    @Autowired InstitutionSignKeyVersionRepository versions;
    @Autowired CredentialIssuerDocumentService issuerDocuments;
    @Autowired CredentialVerificationKeyResolver resolver;
    @Autowired CredentialPublicJwkFactory jwks;

    @Test
    @SuppressWarnings("unchecked")
    void 발급자_문서와_검증_키가_jsonb_에_보존한_JWK_를_같은_공개키로_되살린다() throws Exception {
        UUID institutionId = insertInstitution();
        InstitutionSignKey credential = InstitutionSignKey.provisioning(UUID.randomUUID(), institutionId,
            SignKeyPurpose.CREDENTIAL, 1, NOW).activate(1, NOW).rotated(2, NOW).rotated(3, NOW);
        InstitutionSignKey statusList = InstitutionSignKey.provisioning(UUID.randomUUID(), institutionId,
            SignKeyPurpose.STATUS_LIST, 1, NOW).activate(1, NOW);
        keys.insert(credential);
        keys.insert(statusList);
        PublicKey v1 = newKey();
        PublicKey v2 = newKey();
        PublicKey v3 = newKey();
        PublicKey status1 = newKey();
        PublicKey legacy = newKey();
        InstitutionSignKeyVersion row1 = active(credential, 1, v1);
        versions.insert(row1);
        versions.retireIfActive(row1.id(), NOW);
        InstitutionSignKeyVersion row2 = active(credential, 2, v2);
        versions.insert(row2);
        versions.compromise(row2.id(), NOW);
        versions.insert(active(credential, 3, v3));
        versions.insert(active(statusList, 1, status1));
        versions.insert(InstitutionSignKeyVersion.legacy(
            UUID.randomUUID(), credential.id(), institutionId, storedJwk(legacy), NOW));

        Map<String, Object> document = issuerDocuments.document(institutionId);

        String issuer = (String) document.get("id");
        List<Map<String, Object>> methods = (List<Map<String, Object>>) document.get("verificationMethod");
        assertThat(methods).extracting(method -> method.get("id")).containsExactly(
            issuer + "#v3", issuer + "#v1", issuer + "#development-1", issuer + "#status-v1");
        assertThat(document.get("assertionMethod")).isEqualTo(
            List.of(issuer + "#v3", issuer + "#v1", issuer + "#development-1", issuer + "#status-v1"));
        assertThat(((Map<String, String>) methods.get(0).get("publicKeyJwk")).get("x"))
            .isEqualTo(jwks.create("ignored", v3).get("x"));

        assertThat(resolver.resolve(institutionId, "v2", DocumentKind.CREDENTIAL).compromised()).isTrue();
        assertThat(resolver.resolve(institutionId, "v2", DocumentKind.CREDENTIAL).publicKey().getEncoded())
            .isEqualTo(v2.getEncoded());
        assertThat(resolver.resolve(institutionId, "status-v1", DocumentKind.STATUS_LIST).publicKey().getEncoded())
            .isEqualTo(status1.getEncoded());
        assertThat(resolver.resolve(institutionId, "development-1", DocumentKind.STATUS_LIST).publicKey().getEncoded())
            .isEqualTo(legacy.getEncoded());
    }

    private InstitutionSignKeyVersion active(InstitutionSignKey master, int keyVersion, PublicKey key) {
        return InstitutionSignKeyVersion.active(UUID.randomUUID(), master.id(), master.institutionId(),
            master.purpose(), master.generation(), keyVersion, storedJwk(key), NOW, null, NOW);
    }

    private String storedJwk(PublicKey key) {
        Map<String, String> jwk = jwks.create("ignored", key);
        return "{\"kty\":\"OKP\",\"crv\":\"Ed25519\",\"alg\":\"EdDSA\",\"use\":\"sig\",\"x\":\""
            + jwk.get("x") + "\"}";
    }

    private static PublicKey newKey() throws Exception {
        return KeyPairGenerator.getInstance("Ed25519").generateKeyPair().getPublic();
    }

    private UUID insertInstitution() {
        UUID institutionId = UUID.randomUUID();
        jdbcTemplate.update(
            "INSERT INTO tb_institutions (id, institution_code, name) VALUES (?, ?, ?)",
            institutionId, uniqueCode("institution"), uniqueCode("institution-name"));
        return institutionId;
    }
}
