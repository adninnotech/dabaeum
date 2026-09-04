package com.adn.dabaeum.credential.infrastructure.crypto;

import com.adn.dabaeum.credential.domain.CredentialPublicJwkFactory;
import java.security.PublicKey;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

public class Ed25519PublicJwkFactory implements CredentialPublicJwkFactory {

    private static final byte[] RFC_8410_SPKI_PREFIX =
        HexFormat.of().parseHex("302a300506032b6570032100");
    private static final int RAW_KEY_LENGTH = 32;

    @Override
    public Map<String, String> create(String keyId, PublicKey publicKey) {
        if (keyId == null || keyId.isBlank() || publicKey == null) {
            throw invalid();
        }
        byte[] encoded = publicKey.getEncoded();
        if (encoded == null
            || encoded.length != RFC_8410_SPKI_PREFIX.length + RAW_KEY_LENGTH
            || !Arrays.equals(encoded, 0, RFC_8410_SPKI_PREFIX.length,
                RFC_8410_SPKI_PREFIX, 0, RFC_8410_SPKI_PREFIX.length)) {
            throw invalid();
        }
        byte[] rawKey = Arrays.copyOfRange(
            encoded, RFC_8410_SPKI_PREFIX.length, encoded.length);

        Map<String, String> jwk = new LinkedHashMap<>();
        jwk.put("kid", keyId);
        jwk.put("kty", "OKP");
        jwk.put("crv", "Ed25519");
        jwk.put("alg", "EdDSA");
        jwk.put("use", "sig");
        jwk.put("x", Base64.getUrlEncoder().withoutPadding().encodeToString(rawKey));
        return jwk;
    }

    private IllegalArgumentException invalid() {
        return new IllegalArgumentException("Ed25519 public key is invalid");
    }
}
