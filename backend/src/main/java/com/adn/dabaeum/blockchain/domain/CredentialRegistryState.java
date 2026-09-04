package com.adn.dabaeum.blockchain.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.regex.Pattern;

/** Business Layer에 반환하는 Provider 중립 Credential 원장 상태이다. */
public record CredentialRegistryState(
    String chainKey,
    int schemaVersion,
    RegistryStatus status,
    String vcHash,
    Instant eventTime,
    BlockchainProvider provider
) {
    private static final Pattern SHA_256_PATTERN = Pattern.compile("^[0-9a-f]{64}$");

    public CredentialRegistryState {
        chainKey = requireText(chainKey, "chainKey");
        requireSchemaVersion(schemaVersion);
        Objects.requireNonNull(status, "status");
        vcHash = requireHash(vcHash);
        Objects.requireNonNull(eventTime, "eventTime");
        Objects.requireNonNull(provider, "provider");
    }

    static int requireSchemaVersion(int value) {
        if (value != 1) throw new IllegalArgumentException("schemaVersion must be 1");
        return value;
    }

    static String requireHashVersion(String value) {
        if (!"ENVELOPE_SHA256_V0".equals(value)
            && !"COMPACT_JWS_SHA256_V1".equals(value)) {
            throw new IllegalArgumentException("hashVersion is not supported");
        }
        return value;
    }

    static String requireHashVersionForReference(
        CredentialRegistryReference reference,
        String value
    ) {
        requireHashVersion(value);
        String expected = reference.isLegacy()
            ? "ENVELOPE_SHA256_V0" : "COMPACT_JWS_SHA256_V1";
        if (!expected.equals(value)) {
            throw new IllegalArgumentException("hashVersion does not match registry reference");
        }
        return value;
    }

    static String requireHash(String value) {
        if (value == null || !SHA_256_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("vcHash must be a lowercase SHA-256 hex value");
        }
        return value;
    }

    static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value;
    }
}
