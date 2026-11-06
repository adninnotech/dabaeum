package com.adn.dabaeum.recommendation.api;

import com.adn.dabaeum.recommendation.application.RecommendationReason;
import com.adn.dabaeum.recommendation.domain.RecommendationReasonType;
import com.adn.dabaeum.recommendation.domain.RecommendationTopFactor;
import com.adn.dabaeum.recommendation.domain.ReferenceSignal;
import java.math.BigDecimal;
import java.util.UUID;

/** yaml {@code RecommendationReason}. type 에 따라 쓰이는 필드가 다르고 나머지는 null 이다. */
public record RecommendationReasonResponse(
    RecommendationReasonType type,
    String classificationCode,
    String classificationName,
    ReferenceSignal sourceSignal,
    UUID sourceCourseId,
    String sourceCourseTitle,
    String ageBand,
    BigDecimal score,
    RecommendationTopFactor topFactor
) {

    static RecommendationReasonResponse from(RecommendationReason reason) {
        return new RecommendationReasonResponse(
            reason.type(), reason.classificationCode(), reason.classificationName(),
            reason.sourceSignal(), reason.sourceCourseId(), reason.sourceCourseTitle(), reason.ageBand(),
            reason.score(), reason.topFactor());
    }
}
