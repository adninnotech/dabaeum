package com.adn.dabaeum.credential.infrastructure.mybatis;

import java.time.Instant;
import java.util.UUID;

public record KmsApprovalRow(
    UUID id,
    UUID institutionId,
    String purpose,
    String action,
    UUID signKeyId,
    Integer expectedVersion,
    Integer targetKeyVersion,
    Instant targetCompromisedAt,
    String paramsHash,
    UUID approvedBy,
    Instant expiresAt,
    Instant consumedAt,
    UUID consumedByOperationId,
    Instant createdAt
) {
}
