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
    Instant updatedAt,
    /** 정의로 준 배지면 정의 id·code·이미지(설계서 §11). 정의 없이 준 옛 배지는 모두 null. */
    UUID definitionId,
    String definitionCode,
    UUID imageFileId,
    /** 조회할 때만 채운다(설계서 §11.4). 저장할 때는 null. */
    Details details
) {

    /** 수혜자·단계·유효성·최근 변동. rule 은 정의 규칙(정의 없는 옛 배지는 null). */
    public record Details(
        String recipientName,
        Integer tierLevel,
        Instant expiresAt,
        BadgeRule rule,
        LearningBadgeEvent lastEvent
    ) {
    }

    public LearningBadge(
        UUID id, UUID userId, UUID courseId, UUID credentialId, String badgeType, String badgeName,
        LearningBadgeStatus status, String nftTokenId, Instant issuedAt, Instant revokedAt,
        Instant createdAt, Instant updatedAt, UUID definitionId, String definitionCode, UUID imageFileId
    ) {
        this(id, userId, courseId, credentialId, badgeType, badgeName, status, nftTokenId, issuedAt, revokedAt,
            createdAt, updatedAt, definitionId, definitionCode, imageFileId, null);
    }

    public LearningBadge(
        UUID id, UUID userId, UUID courseId, UUID credentialId, String badgeType, String badgeName,
        LearningBadgeStatus status, String nftTokenId, Instant issuedAt, Instant revokedAt,
        Instant createdAt, Instant updatedAt
    ) {
        this(id, userId, courseId, credentialId, badgeType, badgeName, status, nftTokenId, issuedAt, revokedAt,
            createdAt, updatedAt, null, null, null, null);
    }
}
