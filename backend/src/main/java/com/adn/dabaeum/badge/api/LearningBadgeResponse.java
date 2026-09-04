package com.adn.dabaeum.badge.api;

import com.adn.dabaeum.badge.domain.LearningBadgeStatus;
import java.time.Instant;
import java.util.UUID;

public record LearningBadgeResponse(
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
