package com.adn.dabaeum.recommendation.domain;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * 모델 점수 행 하나 (설계 §12.5, tb_course_recommendation_scores).
 *
 * @param score 신청 확률 0~1
 * @param topFactor 이 강좌 점수에 가장 크게 기여한 속성 묶음
 */
public record ScoredCourse(UUID courseId, BigDecimal score, RecommendationTopFactor topFactor) {

    public ScoredCourse {
        Objects.requireNonNull(courseId, "courseId");
        Objects.requireNonNull(score, "score");
        Objects.requireNonNull(topFactor, "topFactor");
        if (score.signum() < 0 || score.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("score must be 0..1");
        }
    }
}
