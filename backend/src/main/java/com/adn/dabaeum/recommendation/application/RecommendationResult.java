package com.adn.dabaeum.recommendation.application;

import com.adn.dabaeum.recommendation.domain.RecommendationBasis;
import java.util.List;
import java.util.Objects;

/** 추천 응답. basis 는 첫 항목의 출처. 항목이 없으면 POPULAR(프론트가 섹션을 숨긴다). */
public record RecommendationResult(RecommendationBasis basis, List<RecommendedCourse> items) {

    public RecommendationResult {
        Objects.requireNonNull(basis, "basis");
        items = List.copyOf(Objects.requireNonNullElse(items, List.of()));
    }
}
