package com.adn.dabaeum.recommendation.api;

import com.adn.dabaeum.recommendation.domain.RecommendationBasis;
import java.util.List;

/** yaml {@code RecommendationListResponse}. basis 는 첫 항목의 근거 — 프론트가 섹션 제목을 고른다. */
public record RecommendationListResponse(RecommendationBasis basis, List<RecommendedCourseResponse> items) {
}
