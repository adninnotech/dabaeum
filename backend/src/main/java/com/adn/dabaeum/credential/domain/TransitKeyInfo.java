package com.adn.dabaeum.credential.domain;

import java.security.PublicKey;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/** {@code GET {mount}/keys/{name}} 의 해석 결과 (설계서 §5.3 재조회 검증, §15.1 관측 기록). */
public record TransitKeyInfo(
    String type,
    boolean exportable,
    boolean allowPlaintextBackup,
    boolean deletionAllowed,
    boolean derived,
    int latestVersion,
    int minEncryptionVersion,
    long autoRotatePeriodSeconds,
    Map<Integer, Version> versions
) {

    public static final String ED25519 = "ed25519";

    public record Version(PublicKey publicKey, Instant creationTime) {

        public Version {
            Objects.requireNonNull(publicKey, "publicKey");
            Objects.requireNonNull(creationTime, "creationTime");
        }
    }

    public TransitKeyInfo {
        Objects.requireNonNull(type, "type");
        versions = Map.copyOf(Objects.requireNonNull(versions, "versions"));
        if (latestVersion < 1 || !versions.containsKey(latestVersion)) {
            throw new IllegalArgumentException("latestVersion must be present in versions");
        }
    }

    /** §5.3 설계값. 어긋나면 ACTIVE 로 올리지 않는다. */
    public boolean matchesDesignDefaults() {
        return ED25519.equals(type) && !exportable && !allowPlaintextBackup && !deletionAllowed && !derived
            && autoRotatePeriodSeconds == 0;
    }

    public Version version(int keyVersion) {
        Version version = versions.get(keyVersion);
        if (version == null) {
            throw new IllegalArgumentException("no such key version: " + keyVersion);
        }
        return version;
    }
}
