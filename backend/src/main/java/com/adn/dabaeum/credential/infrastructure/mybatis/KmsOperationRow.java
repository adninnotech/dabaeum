package com.adn.dabaeum.credential.infrastructure.mybatis;

import java.time.Instant;
import java.util.UUID;

public record KmsOperationRow(
    UUID id,
    UUID signKeyId,
    UUID institutionId,
    String purpose,
    String action,
    String status,
    Integer targetKeyVersion,
    Integer expectedVersionBefore,
    Integer observedVersionAfter,
    Instant preparedAt,
    Instant lastCallAt,
    String owner,
    Instant leaseUntil,
    String beforeValue,
    String afterValue,
    String reason,
    UUID requestedBy,
    UUID approvalId,
    String idempotencyKey,
    Instant createdAt,
    Instant updatedAt
) {
}
