package com.adn.dabaeum.credential.infrastructure.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.Map;
import org.junit.jupiter.api.Test;

class Ed25519PublicJwkFactoryTest {

    private final Ed25519PublicJwkFactory factory = new Ed25519PublicJwkFactory();

    @Test
    void extractsRfc8410RawEd25519KeyAsPublicJwk() throws Exception {
        KeyPair pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        byte[] encoded = pair.getPublic().getEncoded();
        String expectedX = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(java.util.Arrays.copyOfRange(encoded, encoded.length - 32, encoded.length));

        Map<String, String> jwk = factory.create(
            "https://vc.example.test/api/v1/vc/issuers/issuer#development-1",
            pair.getPublic());

        assertThat(jwk).containsExactly(
            org.assertj.core.data.MapEntry.entry("kid",
                "https://vc.example.test/api/v1/vc/issuers/issuer#development-1"),
            org.assertj.core.data.MapEntry.entry("kty", "OKP"),
            org.assertj.core.data.MapEntry.entry("crv", "Ed25519"),
            org.assertj.core.data.MapEntry.entry("alg", "EdDSA"),
            org.assertj.core.data.MapEntry.entry("use", "sig"),
            org.assertj.core.data.MapEntry.entry("x", expectedX));
        assertThat(jwk.get("x")).doesNotContain("=");
    }

    @Test
    void rejectsNonEd25519AndMalformedSpkiWithoutExposingKeyBytes() throws Exception {
        KeyPair rsa = KeyPairGenerator.getInstance("RSA").generateKeyPair();

        assertThatThrownBy(() -> factory.create("https://vc.example.test/key", rsa.getPublic()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Ed25519 public key is invalid");
    }
}
