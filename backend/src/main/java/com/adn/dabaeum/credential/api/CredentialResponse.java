package com.adn.dabaeum.credential.api;

import com.adn.dabaeum.credential.domain.CredentialStatus;
import java.time.Instant;
import java.util.UUID;

public record CredentialResponse(
    UUID id,
    UUID credentialGroupId,
    UUID previousCredentialId,
    String credentialNo,
    int versionNo,
    String issuerIdentifier,
    String subjectIdentifier,
    String credentialType,
    CredentialStatus status,
    Instant validFrom,
    Instant validUntil,
    String vcHash,
    Instant issuedAt,
    Instant revokedAt,
    String revocationReason,
    Instant createdAt,
    Instant updatedAt
) {
}
