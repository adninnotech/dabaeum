package com.adn.dabaeum.recommendation.application;

import com.adn.dabaeum.recommendation.domain.InterestCourse;
import com.adn.dabaeum.recommendation.domain.RecommendationReasonType;
import com.adn.dabaeum.recommendation.domain.RecommendationTopFactor;
import com.adn.dabaeum.recommendation.domain.ReferenceSignal;
import com.adn.dabaeum.recommendation.domain.ScoredCourse;
import com.adn.dabaeum.recommendation.domain.SimilarCourse;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * 추천 이유 (설계 §6, §12.5). type 에 따라 채워지는 필드가 다르고 나머지는 null.
 * score·topFactor 는 MODEL_SCORE 일 때만 있다.
 */
public record RecommendationReason(
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

    public RecommendationReason {
        Objects.requireNonNull(type, "type");
    }

    static RecommendationReason modelScore(ScoredCourse scored) {
        return new RecommendationReason(RecommendationReasonType.MODEL_SCORE,
            null, null, null, null, null, null, scored.score(), scored.topFactor());
    }

    static RecommendationReason similar(SimilarCourse similar) {
        return new RecommendationReason(RecommendationReasonType.SIMILAR_CLASSIFICATION,
            similar.matchedClassificationCode(), similar.matchedClassificationName(), similar.sourceSignal(),
            similar.sourceCourseId(), similar.sourceCourseTitle(), null, null, null);
    }

    static RecommendationReason popularAgeBand(String ageBand) {
        return new RecommendationReason(RecommendationReasonType.POPULAR_AGE_BAND,
            null, null, null, null, null, ageBand, null, null);
    }

    static RecommendationReason interest(InterestCourse course) {
        return new RecommendationReason(RecommendationReasonType.INTEREST,
            course.classificationCode(), course.classificationName(), null, null, null, null, null, null);
    }

    static RecommendationReason popular() {
        return new RecommendationReason(RecommendationReasonType.POPULAR,
            null, null, null, null, null, null, null, null);
    }
}
