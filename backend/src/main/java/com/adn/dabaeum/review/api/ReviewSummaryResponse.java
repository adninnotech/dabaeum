package com.adn.dabaeum.review.api;

import com.adn.dabaeum.review.domain.CourseReviewSummary;
import java.math.BigDecimal;
import java.util.Map;

public record ReviewSummaryResponse(
    long totalCount,
    BigDecimal averageRating,
    Map<String, Long> distribution,
    Long writableCount
) {

    public static ReviewSummaryResponse of(CourseReviewSummary summary, Long writableCount) {
        return new ReviewSummaryResponse(
            summary.totalCount(), summary.averageRating(),
            summary.distribution(), writableCount);
    }
}
