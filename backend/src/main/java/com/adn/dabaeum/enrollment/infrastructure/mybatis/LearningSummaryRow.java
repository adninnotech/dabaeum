package com.adn.dabaeum.enrollment.infrastructure.mybatis;

public record LearningSummaryRow(
    Long applying,
    Long inProgress,
    Long finished
) {
}
