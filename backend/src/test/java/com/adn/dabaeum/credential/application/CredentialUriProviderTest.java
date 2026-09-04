package com.adn.dabaeum.credential.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.adn.dabaeum.credential.config.VcProperties;
import com.adn.dabaeum.credential.domain.CredentialUriProvider;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CredentialUriProviderTest {

    private final CredentialUriProvider provider = new DefaultCredentialUriProvider(
        new VcProperties("https://vc.example.test/", "v1", "development-1"));

    @Test
    void createsVersionedPublicCredentialUrisDeterministically() {
        UUID institutionId = UUID.fromString("4248ab4f-27a1-4bfd-a09a-f863e2ffaeb1");
        String issuer = "https://vc.example.test/api/v1/vc/issuers/" + institutionId;

        assertThat(provider.contextUrl()).isEqualTo(
            "https://vc.example.test/api/v1/vc/contexts/lifelong-education/v1");
        assertThat(provider.vocabularyUrl()).isEqualTo(
            "https://vc.example.test/api/v1/vc/vocabulary/lifelong-education/v1");
        assertThat(provider.issuerUrl(institutionId)).isEqualTo(issuer);
        assertThat(provider.keyId(institutionId)).isEqualTo(issuer + "#development-1");
        assertThat(provider.statusUrl("CERT-ENR-8")).isEqualTo(
            "https://vc.example.test/api/v1/vc/status/CERT-ENR-8");
        UUID listId = UUID.fromString("70000000-0000-0000-0000-000000000007");
        assertThat(provider.statusListUrl(listId)).isEqualTo(
            "https://vc.example.test/api/v1/vc/status-lists/" + listId);
    }

    @Test
    void rejectsMissingOrUnsafePathValues() {
        assertThatThrownBy(() -> provider.issuerUrl(null))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> provider.statusUrl(null))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> provider.statusListUrl(null))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> provider.statusUrl("CERT/../8"))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
