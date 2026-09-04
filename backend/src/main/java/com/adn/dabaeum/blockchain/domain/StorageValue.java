package com.adn.dabaeum.blockchain.domain;

import java.time.Instant;

/** DaeguChain Storage와 호환되는 Credential 원장 값이다. */
public record StorageValue(
    int schemaVersion,
    RegistryStatus status,
    String vcHash,
    Instant eventTime
) {

    public StorageValue {
        CredentialRegistryState.requireSchemaVersion(schemaVersion);
        if (status == null) {
            throw new IllegalArgumentException("status is required");
        }
        vcHash = CredentialRegistryState.requireHash(vcHash);
        requireCanonicalEpochMillis(eventTime);
    }

    public String encode() {
        return schemaVersion + "|" + status.code() + "|" + vcHash + "|"
            + eventTime.toEpochMilli();
    }

    public static StorageValue decode(String encoded) {
        if (encoded == null) {
            throw new IllegalArgumentException("storage value is required");
        }
        String[] fields = encoded.split("\\|", -1);
        if (fields.length != 4 || !"1".equals(fields[0])) {
            throw new IllegalArgumentException("storage value is not canonical");
        }

        RegistryStatus status = decodeStatus(fields[1]);
        String hash = CredentialRegistryState.requireHash(fields[2]);
        long epochMillis = parseCanonicalEpochMillis(fields[3]);
        return new StorageValue(1, status, hash, Instant.ofEpochMilli(epochMillis));
    }

    private static RegistryStatus decodeStatus(String code) {
        return switch (code) {
            case "A" -> RegistryStatus.ACTIVE;
            case "R" -> RegistryStatus.REVOKED;
            case "S" -> RegistryStatus.SUPERSEDED;
            default -> throw new IllegalArgumentException("storage status is not supported");
        };
    }

    private static long parseCanonicalEpochMillis(String value) {
        final long epochMillis;
        try {
            epochMillis = Long.parseLong(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("epochMillis is invalid", exception);
        }
        if (epochMillis <= 0 || !Long.toString(epochMillis).equals(value)) {
            throw new IllegalArgumentException("epochMillis is not canonical");
        }
        return epochMillis;
    }

    private static void requireCanonicalEpochMillis(Instant value) {
        if (value == null) {
            throw new IllegalArgumentException("eventTime is required");
        }
        final long epochMillis;
        try {
            epochMillis = value.toEpochMilli();
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("eventTime is outside epochMillis range", exception);
        }
        if (epochMillis <= 0 || !Instant.ofEpochMilli(epochMillis).equals(value)) {
            throw new IllegalArgumentException("eventTime must be a positive epochMillis instant");
        }
    }
}
