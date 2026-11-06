package com.adn.dabaeum.badge.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 배지 변동 이력 (설계서 §11.4). 발급·갱신(단계 상승·하강)·취소·복구를 사유와 함께 남긴다. 고치지 않는다.
 * 자동 변동은 actorId 가 null 이다.
 */
public record LearningBadgeEvent(
    UUID id,
    UUID badgeId,
    Type type,
    Integer fromLevel,
    Integer toLevel,
    String reason,
    UUID actorId,
    Instant occurredAt
) {

    public static final int MAX_REASON = 200;

    public enum Type {
        ISSUED,
        RENEWED,
        REVOKED,
        RESTORED
    }

    public LearningBadgeEvent {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(badgeId, "badgeId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(occurredAt, "occurredAt");
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("reason is required");
        }
        reason = reason.length() > MAX_REASON ? reason.substring(0, MAX_REASON) : reason;
    }

    public static LearningBadgeEvent of(UUID badgeId, Type type, Integer fromLevel, Integer toLevel, String reason,
        UUID actorId, Instant at) {
        return new LearningBadgeEvent(UUID.randomUUID(), badgeId, type, fromLevel, toLevel, reason, actorId, at);
    }
}
