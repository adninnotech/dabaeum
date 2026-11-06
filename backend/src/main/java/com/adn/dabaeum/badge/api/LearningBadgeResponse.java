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
    Instant updatedAt,
    UUID definitionId,
    String definitionCode,
    String imageUrl,
    String issuerName,
    String recipientName,
    Integer tierLevel,
    String tierName,
    String criteria,
    Instant expiresAt,
    LastEvent lastEvent
) {

    /** 가장 최근 변동. type 이 RENEWED 면 화면은 상태를 "갱신" 으로 보여 준다. */
    public record LastEvent(String type, String reason, Instant occurredAt) {
    }
}
