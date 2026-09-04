package com.adn.dabaeum.blockchain.domain;

import java.time.Instant;
import java.util.Objects;

/** Credential 원장 상태 생성 명령이다. */
public record CredentialRegistryCreate(
    CredentialRegistryReference reference,
    int schemaVersion,
    RegistryStatus status,
    String vcHash,
    String hashVersion,
    Instant eventTime,
    LegacyCredentialIssueDetails legacyDetails
) {
    public CredentialRegistryCreate {
        Objects.requireNonNull(reference, "reference");
        CredentialRegistryState.requireSchemaVersion(schemaVersion);
        Objects.requireNonNull(status, "status");
        if (status != RegistryStatus.ACTIVE) {
            throw new IllegalArgumentException("create status must be ACTIVE");
        }
        vcHash = CredentialRegistryState.requireHash(vcHash);
        hashVersion = CredentialRegistryState.requireHashVersionForReference(
            reference, hashVersion
        );
        Objects.requireNonNull(eventTime, "eventTime");
        if (reference.isLegacy()) {
            Objects.requireNonNull(legacyDetails, "legacyDetails");
        } else if (legacyDetails != null) {
            throw new IllegalArgumentException("legacyDetails is only supported by legacy references");
        }
    }
}
