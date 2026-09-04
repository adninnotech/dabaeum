package com.adn.dabaeum.credential.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.adn.dabaeum.common.api.ApiException;
import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import com.adn.dabaeum.credential.domain.Credential;
import com.adn.dabaeum.credential.domain.CredentialDocument;
import com.adn.dabaeum.credential.domain.CredentialProofService;
import com.adn.dabaeum.credential.domain.CredentialStatus;
import com.adn.dabaeum.credential.domain.CredentialView;
import com.adn.dabaeum.credential.domain.SignedCredentialEnvelope;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.ObjectMapper;

class CredentialDocumentDownloadServiceTest {

    private static final UUID CREDENTIAL_ID = UUID.fromString(
        "70000000-0000-0000-0000-000000000007");
    private static final Instant NOW = Instant.parse("2026-08-10T00:00:00Z");

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final CredentialApplicationService credentials = mock(CredentialApplicationService.class);
    private final CredentialProofService proof = mock(CredentialProofService.class);
    private final AuthenticatedUserContext actor = mock(AuthenticatedUserContext.class);
    private final CredentialHashService hashes = new CredentialHashService(objectMapper);
    private final CredentialDocumentDownloadService service =
        new DefaultCredentialDocumentDownloadService(
            credentials, proof, hashes, objectMapper);

    @Test
    void downloadsVerifiedCompactJwsForEveryHistoricalIssuedState() throws Exception {
        for (CredentialStatus status : new CredentialStatus[]{
            CredentialStatus.ISSUED, CredentialStatus.REVOKED,
            CredentialStatus.SUPERSEDED, CredentialStatus.EXPIRED
        }) {
            Credential credential = credential(status, null);
            when(credentials.get(CREDENTIAL_ID, actor)).thenReturn(new CredentialView(credential, null));
            when(proof.verify(new SignedCredentialEnvelope(
                "application/vc+jwt", "header.payload.signature")))
                .thenReturn(new CredentialDocument("{\"verified\":true}"));

            CredentialDocumentDownloadService.Download result =
                service.download(CREDENTIAL_ID, actor);

            assertThat(result.credentialNo())
                .isEqualTo("CERT-70000000000000000000000000000007");
            assertThat(result.compactJws()).isEqualTo("header.payload.signature");
        }
    }

    @Test
    void currentHashVersionVerifiesCompactJwsInsteadOfEnvelopeJson() throws Exception {
        SignedCredentialEnvelope envelope = new SignedCredentialEnvelope(
            "application/vc+jwt", "header.payload.signature");
        String payload = hashes.serialize(envelope);
        Credential current = new Credential(
            CREDENTIAL_ID, UUID.randomUUID(), null,
            "CERT-70000000000000000000000000000007", 1,
            "https://vc.example.test/api/v1/vc/issuers/" + UUID.randomUUID(),
            "urn:dabaeum:user:" + UUID.randomUUID(),
            "LIFELONG_EDUCATION_COMPLETION", CredentialStatus.ISSUED, NOW, null,
            payload, hashes.sha256CompactJws(envelope.compactJws()),
            "CURRENTKEY000001", "COMPACT_JWS_SHA256_V1", NOW,
            null, null, null, null, NOW, NOW);
        when(credentials.get(CREDENTIAL_ID, actor)).thenReturn(new CredentialView(current, null));
        when(proof.verify(envelope)).thenReturn(new CredentialDocument("{\"verified\":true}"));

        CredentialDocumentDownloadService.Download result = service.download(CREDENTIAL_ID, actor);

        assertThat(result.compactJws()).isEqualTo("header.payload.signature");
    }

    @Test
    void rejectsCredentialsThatHaveNotProducedDownloadableMaterial() {
        for (CredentialStatus status : new CredentialStatus[]{
            CredentialStatus.PENDING, CredentialStatus.ISSUING, CredentialStatus.FAILED
        }) {
            when(credentials.get(CREDENTIAL_ID, actor))
                .thenReturn(new CredentialView(credential(status, null), null));

            assertThatThrownBy(() -> service.download(CREDENTIAL_ID, actor))
                .isInstanceOfSatisfying(ApiException.class,
                    exception -> assertThat(exception.status()).isEqualTo(HttpStatus.CONFLICT));
        }
    }

    @Test
    void rejectsStoredEnvelopeHashMismatchWithoutReturningRawMaterial() {
        Credential credential = credential(CredentialStatus.ISSUED, "0".repeat(64));
        when(credentials.get(CREDENTIAL_ID, actor)).thenReturn(new CredentialView(credential, null));

        assertThatThrownBy(() -> service.download(CREDENTIAL_ID, actor))
            .isInstanceOfSatisfying(ApiException.class, exception -> {
                assertThat(exception.status()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
                assertThat(exception.getMessage()).doesNotContain("header.payload.signature");
            });
    }

    @Test
    void preservesAuthorizationFailureFromCredentialDetailScope() {
        ApiException forbidden = new ApiException(
            HttpStatus.FORBIDDEN,
            com.adn.dabaeum.common.api.ApiErrorCode.INSTITUTION_SCOPE_FORBIDDEN,
            "Institution scope is not allowed");
        when(credentials.get(CREDENTIAL_ID, actor)).thenThrow(forbidden);

        assertThatThrownBy(() -> service.download(CREDENTIAL_ID, actor)).isSameAs(forbidden);
    }

    private Credential credential(CredentialStatus status, String forcedHash) {
        if (status == CredentialStatus.PENDING) {
            return new Credential(CREDENTIAL_ID, UUID.randomUUID(), null,
                "CERT-70000000000000000000000000000007", 1, null, null,
                "LIFELONG_EDUCATION_COMPLETION", status, null, null, null, null, null,
                null, null, null, null, NOW, NOW);
        }
        if (status == CredentialStatus.FAILED) {
            return new Credential(CREDENTIAL_ID, UUID.randomUUID(), null,
                "CERT-70000000000000000000000000000007", 1, null, null,
                "LIFELONG_EDUCATION_COMPLETION", status, null, null, null, null, null,
                null, null, "PROOF_FAILED", "Credential issuance failed", NOW, NOW);
        }
        SignedCredentialEnvelope envelope = new SignedCredentialEnvelope(
            "application/vc+jwt", "header.payload.signature");
        String payload = hashes.serialize(envelope);
        String hash = forcedHash == null ? hashes.sha256Payload(payload) : forcedHash;
        if (status == CredentialStatus.ISSUING) {
            return new Credential(CREDENTIAL_ID, UUID.randomUUID(), null,
                "CERT-70000000000000000000000000000007", 1,
                "https://vc.example.test/api/v1/vc/issuers/" + UUID.randomUUID(),
                "urn:dabaeum:user:" + UUID.randomUUID(),
                "LIFELONG_EDUCATION_COMPLETION", status, NOW, null, payload, hash, NOW,
                null, null, null, null, NOW, NOW);
        }
        return new Credential(CREDENTIAL_ID, UUID.randomUUID(), null,
            "CERT-70000000000000000000000000000007", 1,
            "https://vc.example.test/api/v1/vc/issuers/" + UUID.randomUUID(),
            "urn:dabaeum:user:" + UUID.randomUUID(),
            "LIFELONG_EDUCATION_COMPLETION", status, NOW, null, payload, hash, NOW,
            status == CredentialStatus.REVOKED ? NOW : null,
            status == CredentialStatus.REVOKED ? "administrative correction" : null,
            null, null, NOW, NOW);
    }
}
