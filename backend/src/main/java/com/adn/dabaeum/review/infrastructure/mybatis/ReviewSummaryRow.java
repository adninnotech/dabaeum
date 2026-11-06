package com.adn.dabaeum.review.infrastructure.mybatis;

import java.math.BigDecimal;

public record ReviewSummaryRow(
    Long totalCount,
    BigDecimal averageRating,
    Long count5,
    Long count4,
    Long count3,
    Long count2,
    Long count1
) {
}
