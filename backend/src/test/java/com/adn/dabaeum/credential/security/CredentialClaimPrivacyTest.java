package com.adn.dabaeum.credential.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.adn.dabaeum.credential.application.CredentialDocumentFactory;
import com.adn.dabaeum.credential.domain.CredentialStatusListEntry;
import com.adn.dabaeum.credential.application.DabaeumUrnCredentialIdentifierProvider;
import com.adn.dabaeum.credential.application.DefaultCredentialUriProvider;
import com.adn.dabaeum.credential.config.VcProperties;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class CredentialClaimPrivacyTest {

    @Test
    void serializedCredentialContainsNoPersonalOrDisplayClaimsOrRawDid() {
        var factory = new CredentialDocumentFactory(
            new ObjectMapper(), new DabaeumUrnCredentialIdentifierProvider(
                new DefaultCredentialUriProvider(new VcProperties(
                    "https://vc.example.test", "v1", "development-1"))));

        String payload = factory.create(
            UUID.fromString("10000000-0000-0000-0000-000000000001"),
            new CredentialStatusListEntry(
                UUID.fromString("70000000-0000-0000-0000-000000000007"), 94_567),
            UUID.fromString("20000000-0000-0000-0000-000000000002"),
            UUID.fromString("30000000-0000-0000-0000-000000000003"),
            UUID.fromString("40000000-0000-0000-0000-000000000004"),
            UUID.fromString("50000000-0000-0000-0000-000000000005"),
            UUID.fromString("60000000-0000-0000-0000-000000000006"),
            Instant.parse("2026-08-05T01:02:03Z"), null,
            Instant.parse("2026-08-04T10:20:30Z"), new BigDecimal("90.00"), 1200, null
        ).canonicalJson();

        assertThat(payload).doesNotContainIgnoringCase(
            "name", "email", "phone", "birth", "courseTitle", "did:");
        assertThat(payload)
            .contains("https://vc.example.test/api/v1/vc/issuers/", "urn:dabaeum:user:")
            .doesNotContain("urn:dabaeum:institution:");
    }
}
