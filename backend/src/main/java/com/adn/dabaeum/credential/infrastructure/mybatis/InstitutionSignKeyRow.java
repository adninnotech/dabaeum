package com.adn.dabaeum.credential.infrastructure.mybatis;

import java.time.Instant;
import java.util.UUID;

public record InstitutionSignKeyRow(
    UUID id,
    UUID institutionId,
    String purpose,
    Integer generation,
    Boolean isCurrent,
    String mount,
    String keyName,
    String keyType,
    Integer currentVersion,
    String status,
    String statusReason,
    Instant rotatedAt,
    Instant suspendedAt,
    Instant revokedAt,
    Instant compromisedAt,
    Integer versionNo,
    Instant createdAt,
    Instant updatedAt
) {
}
