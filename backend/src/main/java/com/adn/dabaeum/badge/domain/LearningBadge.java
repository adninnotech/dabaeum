package com.adn.dabaeum.badge.domain;

import java.time.Instant;
import java.util.UUID;

public record LearningBadge(
    UUID id,
    UUID userId,
    UUID courseId,
    UUID credentialId,
    String badgeType,
    String badgeName,
    LearningBadgeStatus status,
    String nftTokenId,
    Instant issuedAt,
    Instant revokedAt,
    Instant createdAt,
    Instant updatedAt
) {
}
