package com.adn.dabaeum.credential.infrastructure.mybatis;

import java.time.Instant;
import java.util.UUID;

public record CredentialVerificationRow(
    UUID id,
    UUID credentialId,
    String presentedCredentialNo,
    String presentedHash,
    String verificationType,
    String requesterType,
    String requesterId,
    String result,
    Instant verifiedAt,
    String requestIp,
    String verificationHash,
    String metadata,
    Instant createdAt
) {
}
