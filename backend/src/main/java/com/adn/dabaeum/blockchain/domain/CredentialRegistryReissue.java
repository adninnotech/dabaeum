package com.adn.dabaeum.blockchain.domain;

import java.time.Instant;
import java.util.Objects;

/** 기존 CERT namespace에서만 허용하는 원자적 Credential 재발급 명령이다. */
public record CredentialRegistryReissue(
    CredentialRegistryReference previousReference,
    CredentialRegistryReference replacementReference,
    int schemaVersion,
    String vcHash,
    String hashVersion,
    Instant eventTime,
    LegacyCredentialIssueDetails replacementDetails,
    Instant revokedAt
) {
    public CredentialRegistryReissue {
        Objects.requireNonNull(previousReference, "previousReference");
        Objects.requireNonNull(replacementReference, "replacementReference");
        if (!previousReference.isLegacy() || !replacementReference.isLegacy()) {
            throw new IllegalArgumentException("reissue is only available for legacy references");
        }
        if (previousReference.provider() != replacementReference.provider()) {
            throw new IllegalArgumentException("reissue references must use the same provider");
        }
        if (previousReference.legacyCredentialNo().equals(replacementReference.legacyCredentialNo())) {
            throw new IllegalArgumentException("replacement credential must differ from previous credential");
        }
        CredentialRegistryState.requireSchemaVersion(schemaVersion);
        vcHash = CredentialRegistryState.requireHash(vcHash);
        hashVersion = CredentialRegistryState.requireHashVersionForReference(
            previousReference, hashVersion
        );
        Objects.requireNonNull(eventTime, "eventTime");
        Objects.requireNonNull(replacementDetails, "replacementDetails");
        Objects.requireNonNull(revokedAt, "revokedAt");
    }
}
