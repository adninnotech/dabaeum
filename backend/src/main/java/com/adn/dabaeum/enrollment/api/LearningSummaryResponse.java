package com.adn.dabaeum.enrollment.api;

public record LearningSummaryResponse(
    long applying,
    long inProgress,
    long finished
) {
}
