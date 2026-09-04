package com.adn.dabaeum.credential.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class VcPropertiesTest {

    @Test
    void normalizesAbsoluteHttpBaseUrlAndKeepsVersions() {
        VcProperties properties = new VcProperties(
            "http://vc.example.org:8080/", "v1", "development-1");

        assertThat(properties.publicBaseUrl()).isEqualTo("http://vc.example.org:8080");
        assertThat(properties.contextVersion()).isEqualTo("v1");
        assertThat(properties.keyVersion()).isEqualTo("development-1");
    }

    @Test
    void rejectsRelativeUnsupportedOrMalformedValues() {
        assertThatThrownBy(() -> new VcProperties("/api", "v1", "development-1"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new VcProperties("ftp://example.com", "v1", "development-1"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new VcProperties("https://example.com/path", "v1", "development-1"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new VcProperties("https://example.com", "latest", "development-1"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new VcProperties("https://example.com", "v1", "key/latest"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsUnservedContextVersion() {
        assertThatThrownBy(() -> new VcProperties(
            "https://vc.example.test", "v2", "development-1"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("VC context version is invalid");
    }
}
