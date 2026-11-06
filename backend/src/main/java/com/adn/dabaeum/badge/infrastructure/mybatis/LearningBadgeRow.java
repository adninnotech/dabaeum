package com.adn.dabaeum.badge.infrastructure.mybatis;

import java.time.Instant;
import java.util.UUID;

public record LearningBadgeRow(
    UUID id,
    UUID userId,
    UUID courseId,
    UUID credentialId,
    String badgeType,
    String badgeName,
    String status,
    String nftTokenId,
    Instant issuedAt,
    Instant revokedAt,
    Instant createdAt,
    Instant updatedAt,
    UUID definitionId,
    String definitionCode,
    UUID imageFileId,
    String recipientName,
    Integer tierLevel,
    Instant expiresAt,
    String ruleType,
    String ruleParams,
    String lastEventType,
    String lastEventReason,
    Instant lastEventAt,
    UUID lastEventId
) {
}
