package com.adn.dabaeum.recommendation.application;

import com.adn.dabaeum.course.domain.CourseView;
import com.adn.dabaeum.recommendation.domain.RecommendationBasis;
import java.util.Objects;
import java.util.UUID;

/**
 * 추천 항목 하나. impressionId 는 로그인 사용자에게만 있다(비로그인 노출은 로그에 남기지 않는다).
 * basis 는 이 항목을 뽑은 순위(§12.5 MODEL, §3 PERSONAL 또는 §4 폴백)다 — 목록 전체의 basis 와 다를 수 있다.
 */
public record RecommendedCourse(
    UUID impressionId,
    CourseView course,
    RecommendationBasis basis,
    int matchLevel,
    RecommendationReason reason
) {

    public RecommendedCourse {
        Objects.requireNonNull(course, "course");
        Objects.requireNonNull(basis, "basis");
        Objects.requireNonNull(reason, "reason");
    }

    RecommendedCourse withImpressionId(UUID id) {
        return new RecommendedCourse(id, course, basis, matchLevel, reason);
    }
}
