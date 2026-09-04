package com.adn.dabaeum.credential.infrastructure.mybatis;

import java.time.Instant;
import java.util.UUID;

public record CredentialRow(
    UUID id,
    UUID credentialGroupId,
    UUID previousCredentialId,
    String credentialNo,
    Integer versionNo,
    String issuerIdentifier,
    String subjectIdentifier,
    String credentialType,
    String status,
    Instant validFrom,
    Instant validUntil,
    String vcPayload,
    String vcHash,
    String chainKey,
    String vcHashVersion,
    Instant issuedAt,
    Instant revokedAt,
    String revocationReason,
    String failureCode,
    String failureMessage,
    Instant createdAt,
    Instant updatedAt
) {
}
