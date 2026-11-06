package com.adn.dabaeum.credential.infrastructure.crypto;

import com.adn.dabaeum.credential.domain.CredentialPublicJwkFactory;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

public class Ed25519PublicJwkFactory implements CredentialPublicJwkFactory {

    private static final byte[] RFC_8410_SPKI_PREFIX =
        HexFormat.of().parseHex("302a300506032b6570032100");
    private static final int RAW_KEY_LENGTH = 32;
    private static final List<String> STORED_FIELDS = List.of("kty", "crv", "alg", "use", "x");
    private static final Pattern BASE64URL = Pattern.compile("^[A-Za-z0-9_-]+$");
    private static final ObjectMapper JSON = new ObjectMapper();

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

    @Override
    public PublicKey publicKey(String storedJwk) {
        byte[] rawKey = rawKey(read(storedJwk));
        byte[] encoded = new byte[RFC_8410_SPKI_PREFIX.length + RAW_KEY_LENGTH];
        System.arraycopy(RFC_8410_SPKI_PREFIX, 0, encoded, 0, RFC_8410_SPKI_PREFIX.length);
        System.arraycopy(rawKey, 0, encoded, RFC_8410_SPKI_PREFIX.length, RAW_KEY_LENGTH);
        try {
            return KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(encoded));
        } catch (GeneralSecurityException exception) {
            throw invalid();
        }
    }

    @Override
    public Map<String, String> publish(String keyId, String storedJwk) {
        if (keyId == null || keyId.isBlank()) {
            throw invalid();
        }
        Map<String, String> fields = read(storedJwk);
        rawKey(fields);
        Map<String, String> jwk = new LinkedHashMap<>();
        jwk.put("kid", keyId);
        for (String name : STORED_FIELDS) {
            jwk.put(name, fields.get(name));
        }
        return jwk;
    }

    /** 정확히 다섯 필드이고 모두 문자열이며 Ed25519 서명 키를 가리켜야 한다. 개인키(d)·kid 가 섞이면 거부한다. */
    private Map<String, String> read(String storedJwk) {
        if (storedJwk == null || storedJwk.isBlank()) {
            throw invalid();
        }
        JsonNode node;
        try {
            node = JSON.readTree(storedJwk);
        } catch (RuntimeException exception) {
            throw invalid();
        }
        if (node == null || !node.isObject() || node.size() != STORED_FIELDS.size()) {
            throw invalid();
        }
        Map<String, String> fields = new LinkedHashMap<>();
        for (String name : STORED_FIELDS) {
            JsonNode value = node.get(name);
            if (value == null || !value.isString()) {
                throw invalid();
            }
            fields.put(name, value.asString());
        }
        if (!"OKP".equals(fields.get("kty")) || !"Ed25519".equals(fields.get("crv"))
            || !"EdDSA".equals(fields.get("alg")) || !"sig".equals(fields.get("use"))) {
            throw invalid();
        }
        return fields;
    }

    private byte[] rawKey(Map<String, String> fields) {
        String x = fields.get("x");
        if (!BASE64URL.matcher(x).matches()) {
            throw invalid();
        }
        byte[] rawKey;
        try {
            rawKey = Base64.getUrlDecoder().decode(x);
        } catch (IllegalArgumentException exception) {
            throw invalid();
        }
        if (rawKey.length != RAW_KEY_LENGTH
            || !Base64.getUrlEncoder().withoutPadding().encodeToString(rawKey).equals(x)) {
            throw invalid();
        }
        return rawKey;
    }

    private IllegalArgumentException invalid() {
        return new IllegalArgumentException("Ed25519 public key is invalid");
    }
}
