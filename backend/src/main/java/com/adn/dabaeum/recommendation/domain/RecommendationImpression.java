package com.adn.dabaeum.recommendation.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** 추천 노출 로그 한 줄 (설계 §7). 클릭은 clicked_at 으로 같은 행에 남긴다. */
public record RecommendationImpression(
    UUID id,
    UUID userId,
    UUID courseId,
    RecommendationBasis basis,
    RecommendationReasonType reasonType,
    int matchLevel,
    int position,
    Instant shownAt
) {

    public RecommendationImpression {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(courseId, "courseId");
        Objects.requireNonNull(basis, "basis");
        Objects.requireNonNull(reasonType, "reasonType");
        if (matchLevel < 0 || matchLevel > 3) {
            throw new IllegalArgumentException("matchLevel must be 0..3");
        }
        if (position < 1) {
            throw new IllegalArgumentException("position must be at least 1");
        }
        Objects.requireNonNull(shownAt, "shownAt");
    }
}
