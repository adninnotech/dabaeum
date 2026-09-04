package com.adn.dabaeum.correction.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** 관리자가 단방향 상태 전이를 되돌린 이력. 사유는 필수다. */
public record AdminCorrection(
    UUID id,
    CorrectionTargetType targetType,
    UUID targetId,
    String fromStatus,
    String toStatus,
    String reason,
    UUID correctedBy,
    Instant correctedAt
) {

    public AdminCorrection {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(targetType, "targetType");
        Objects.requireNonNull(targetId, "targetId");
        fromStatus = required(fromStatus, "fromStatus");
        toStatus = required(toStatus, "toStatus");
        reason = required(reason, "reason");
        if (reason.length() > 1000) {
            throw new IllegalArgumentException("reason must not exceed 1000 characters");
        }
        Objects.requireNonNull(correctedBy, "correctedBy");
        Objects.requireNonNull(correctedAt, "correctedAt");
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.trim();
    }
}
