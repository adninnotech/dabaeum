package com.adn.dabaeum.dashboard.domain;

public record InstitutionDashboard(
    long instructorTotal,
    long instructorActive,
    long instructorInactive,
    long instructorWithdrawn,
    long courseCount,
    long enrollmentCount
) {
}
