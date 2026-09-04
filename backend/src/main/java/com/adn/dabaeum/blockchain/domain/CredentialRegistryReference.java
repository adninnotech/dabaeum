package com.adn.dabaeum.blockchain.domain;

import java.util.Objects;
import java.util.regex.Pattern;

/** 신규 Storage key 또는 기존 Credential 번호 중 하나를 가리킨다. */
public record CredentialRegistryReference(
    BlockchainProvider provider,
    String dataKey,
    String legacyCredentialNo
) {
    private static final Pattern DATA_KEY_PATTERN = Pattern.compile("^[A-Z0-9]{16}$");

    public CredentialRegistryReference {
        Objects.requireNonNull(provider, "provider");
        boolean hasDataKey = dataKey != null;
        boolean hasLegacyCredentialNo = legacyCredentialNo != null;
        if (hasDataKey == hasLegacyCredentialNo) {
            throw new IllegalArgumentException("exactly one registry identifier is required");
        }
        if (hasDataKey && !DATA_KEY_PATTERN.matcher(dataKey).matches()) {
            throw new IllegalArgumentException("dataKey must be 16 uppercase alphanumeric characters");
        }
        if (hasLegacyCredentialNo && legacyCredentialNo.isBlank()) {
            throw new IllegalArgumentException("legacyCredentialNo is required");
        }
        if (hasLegacyCredentialNo && provider != BlockchainProvider.FABRIC_POC) {
            throw new IllegalArgumentException("legacyCredentialNo is only supported by FABRIC_POC");
        }
    }

    public boolean isLegacy() {
        return legacyCredentialNo != null;
    }

    public String chainKey() {
        return isLegacy() ? "CERT:" + legacyCredentialNo : "DCSTORE:" + dataKey;
    }
}
