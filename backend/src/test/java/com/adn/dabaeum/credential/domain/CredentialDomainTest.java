package com.adn.dabaeum.credential.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CredentialDomainTest {

    private static final Instant CREATED_AT = Instant.parse("2026-08-05T00:00:00Z");
    private static final Instant ISSUED_AT = CREATED_AT.plusSeconds(60);
    private static final String HASH = "a".repeat(64);

    @Test
    void appliesOnlyAllowedCredentialStateTransitions() {
        Credential pending = pendingCredential();

        Credential issuing = pending.startIssuing(CREATED_AT.plusSeconds(1));
        Credential issued = issuing.markIssued(
            "{\"credentialSubject\":{}}",
            HASH,
            "urn:dabaeum:institution:" + UUID.randomUUID(),
            "urn:dabaeum:user:" + UUID.randomUUID(),
            ISSUED_AT,
            ISSUED_AT
        );

        assertThat(issuing.status()).isEqualTo(CredentialStatus.ISSUING);
        assertThat(issued.status()).isEqualTo(CredentialStatus.ISSUED);
        assertThat(issuing.markFailed("PROOF_FAILURE", "masked", ISSUED_AT).status())
            .isEqualTo(CredentialStatus.FAILED);
        assertThat(issued.markRevoked("duplicate", ISSUED_AT.plusSeconds(1)).status())
            .isEqualTo(CredentialStatus.REVOKED);
        assertThat(issued.markSuperseded(ISSUED_AT.plusSeconds(1)).status())
            .isEqualTo(CredentialStatus.SUPERSEDED);
        assertThat(issued.markExpired(ISSUED_AT.plusSeconds(1)).status())
            .isEqualTo(CredentialStatus.EXPIRED);
    }

    @Test
    void rejectsForbiddenCredentialStateTransitions() {
        Credential pending = pendingCredential();

        assertThatIllegalStateException().isThrownBy(() -> pending.markIssued(
            "{\"credentialSubject\":{}}",
            HASH,
            "urn:dabaeum:institution:" + UUID.randomUUID(),
            "urn:dabaeum:user:" + UUID.randomUUID(),
            ISSUED_AT,
            ISSUED_AT
        )).withMessage("CREDENTIAL_STATE_CONFLICT");
        assertThatIllegalStateException().isThrownBy(() -> pending.markSuperseded(ISSUED_AT))
            .withMessage("CREDENTIAL_STATE_CONFLICT");
        assertThatIllegalStateException().isThrownBy(() -> pending.markFailed(
            "PROOF_FAILURE", "masked", ISSUED_AT
        )).withMessage("CREDENTIAL_STATE_CONFLICT");
    }

    @Test
    void rejectsNonPositiveVersionAndInvalidHash() {
        assertThatIllegalArgumentException().isThrownBy(() -> new Credential(
            UUID.randomUUID(), UUID.randomUUID(), null, "credential-no", 0,
            null, null, "LIFELONG_EDUCATION_COMPLETION", CredentialStatus.PENDING,
            null, null, null, null, null, null, null, null, null, CREATED_AT, CREATED_AT
        ));

        assertThatIllegalArgumentException().isThrownBy(() -> issuingCredential().markIssued(
            "{\"credentialSubject\":{}}",
            "A".repeat(64),
            "urn:dabaeum:institution:" + UUID.randomUUID(),
            "urn:dabaeum:user:" + UUID.randomUUID(),
            ISSUED_AT,
            ISSUED_AT
        ));
        assertThatIllegalArgumentException().isThrownBy(() -> issuingCredential().markIssued(
            "{\"credentialSubject\":{}}",
            "a".repeat(63),
            "urn:dabaeum:institution:" + UUID.randomUUID(),
            "urn:dabaeum:user:" + UUID.randomUUID(),
            ISSUED_AT,
            ISSUED_AT
        ));
    }

    @Test
    void acceptsOnlyLegacyOrCurrentRegistryMetadataCombinations() {
        assertThatIllegalArgumentException().isThrownBy(() -> pendingCredential(
            "lowercase-invalid", "COMPACT_JWS_SHA256_V1"));
        assertThatIllegalArgumentException().isThrownBy(() -> pendingCredential(
            "A1B2C3D4E5F6G7H8", "UNKNOWN"));
        assertThatIllegalArgumentException().isThrownBy(() -> pendingCredential(
            "A1B2C3D4E5F6G7H8", null));
        assertThatIllegalArgumentException().isThrownBy(() -> pendingCredential(
            "A1B2C3D4E5F6G7H8", "ENVELOPE_SHA256_V0"));
        assertThatIllegalArgumentException().isThrownBy(() -> pendingCredential(
            null, "COMPACT_JWS_SHA256_V1"));

        assertThat(pendingCredential())
            .extracting(Credential::chainKey, Credential::vcHashVersion)
            .containsExactly(null, null);
        assertThat(pendingCredential(null, "ENVELOPE_SHA256_V0"))
            .extracting(Credential::chainKey, Credential::vcHashVersion)
            .containsExactly(null, "ENVELOPE_SHA256_V0");
        assertThat(pendingCredential("A1B2C3D4E5F6G7H8", "COMPACT_JWS_SHA256_V1"))
            .extracting(Credential::chainKey, Credential::vcHashVersion)
            .containsExactly("A1B2C3D4E5F6G7H8", "COMPACT_JWS_SHA256_V1");
    }

    @Test
    void requiresCompleteIssuedCredentialValues() {
        Credential issuing = issuingCredential();

        assertThatIllegalArgumentException().isThrownBy(() -> issuing.markIssued(
            null,
            HASH,
            "urn:dabaeum:institution:" + UUID.randomUUID(),
            "urn:dabaeum:user:" + UUID.randomUUID(),
            ISSUED_AT,
            ISSUED_AT
        ));
        assertThatIllegalArgumentException().isThrownBy(() -> issuing.markIssued(
            "{\"credentialSubject\":{}}",
            HASH,
            " ",
            "urn:dabaeum:user:" + UUID.randomUUID(),
            ISSUED_AT,
            ISSUED_AT
        ));
        assertThatIllegalArgumentException().isThrownBy(() -> issuing.markIssued(
            "{\"credentialSubject\":{}}",
            HASH,
            "urn:dabaeum:institution:" + UUID.randomUUID(),
            "urn:dabaeum:user:" + UUID.randomUUID(),
            null,
            ISSUED_AT
        ));
    }

    @Test
    void preservesLeadingAndTrailingWhitespaceInIssuedPayload() {
        String payload = "  { \"credentialSubject\" : {} }  ";

        Credential issued = issuingCredential().markIssued(
            payload,
            HASH,
            "urn:dabaeum:institution:" + UUID.randomUUID(),
            "urn:dabaeum:user:" + UUID.randomUUID(),
            ISSUED_AT,
            ISSUED_AT
        );

        assertThat(issued.vcPayload()).isEqualTo(payload);
    }

    private Credential pendingCredential() {
        return new Credential(
            UUID.randomUUID(), UUID.randomUUID(), null, "credential-no", 1,
            null, null, "LIFELONG_EDUCATION_COMPLETION", CredentialStatus.PENDING,
            null, null, null, null, null, null, null, null, null, CREATED_AT, CREATED_AT
        );
    }

    private Credential pendingCredential(String chainKey, String vcHashVersion) {
        return new Credential(
            UUID.randomUUID(), UUID.randomUUID(), null, "credential-no", 1,
            null, null, "LIFELONG_EDUCATION_COMPLETION", CredentialStatus.PENDING,
            null, null, null, null, chainKey, vcHashVersion, null, null, null, null, null,
            CREATED_AT, CREATED_AT
        );
    }

    private Credential issuingCredential() {
        return pendingCredential().startIssuing(CREATED_AT.plusSeconds(1));
    }
}
