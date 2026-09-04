package com.adn.dabaeum.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UserIdentityTest {

    @Test
    void supportsOnlyApprovedIdentityProviders() {
        assertThat(IdentityProvider.values())
            .containsExactly(
                IdentityProvider.LOCAL,
                IdentityProvider.DADAEGU,
                IdentityProvider.DID
            );
    }

    @Test
    void preservesIdentityFieldsAndRejectsBlankProviderSubject() {
        UUID id = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Instant verifiedAt = Instant.parse("2026-08-04T00:00:00Z");

        UserIdentity identity = new UserIdentity(
            id,
            userId,
            IdentityProvider.DADAEGU,
            " subject-123 ",
            "did:example:123",
            verifiedAt,
            "{\"source\":\"test\"}",
            verifiedAt,
            verifiedAt
        );

        assertThat(identity.id()).isEqualTo(id);
        assertThat(identity.userId()).isEqualTo(userId);
        assertThat(identity.provider()).isEqualTo(IdentityProvider.DADAEGU);
        assertThat(identity.providerSubject()).isEqualTo("subject-123");
        assertThat(identity.externalDid()).isEqualTo("did:example:123");
        assertThat(identity.verifiedAt()).isEqualTo(verifiedAt);
        assertThat(identity.metadata()).isEqualTo("{\"source\":\"test\"}");
        assertThatThrownBy(() -> new UserIdentity(
            id,
            userId,
            IdentityProvider.LOCAL,
            " ",
            null,
            null,
            null,
            verifiedAt,
            verifiedAt
        )).isInstanceOf(IllegalArgumentException.class);
    }
}
