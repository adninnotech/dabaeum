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
    Instant updatedAt
) {
}
