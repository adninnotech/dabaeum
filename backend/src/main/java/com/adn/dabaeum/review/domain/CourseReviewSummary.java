package com.adn.dabaeum.review.domain;

import java.math.BigDecimal;
import java.util.Map;

public record CourseReviewSummary(
    long totalCount,
    BigDecimal averageRating,
    Map<String, Long> distribution
) {
}
