package com.adn.dabaeum.blockchain.domain;

import java.time.Instant;
import java.util.Objects;

/** Credential 원장 상태 갱신 명령이다. */
public record CredentialRegistryUpdate(
    CredentialRegistryReference reference,
    int schemaVersion,
    RegistryStatus status,
    String vcHash,
    String hashVersion,
    Instant eventTime
) {
    public CredentialRegistryUpdate {
        Objects.requireNonNull(reference, "reference");
        CredentialRegistryState.requireSchemaVersion(schemaVersion);
        Objects.requireNonNull(status, "status");
        if (status != RegistryStatus.REVOKED && status != RegistryStatus.SUPERSEDED) {
            throw new IllegalArgumentException("update status must be REVOKED or SUPERSEDED");
        }
        vcHash = CredentialRegistryState.requireHash(vcHash);
        hashVersion = CredentialRegistryState.requireHashVersionForReference(
            reference, hashVersion
        );
        Objects.requireNonNull(eventTime, "eventTime");
    }
}
