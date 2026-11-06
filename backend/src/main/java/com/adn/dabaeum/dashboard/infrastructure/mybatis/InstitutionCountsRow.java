package com.adn.dabaeum.dashboard.infrastructure.mybatis;

public record InstitutionCountsRow(
    Long instructorTotal,
    Long instructorActive,
    Long instructorInactive,
    Long instructorWithdrawn,
    Long courseCount,
    Long enrollmentCount,
    Long courseRecruiting,
    Long courseInProgress,
    Long courseCompleted,
    Long approvedEnrollmentCount,
    Long completionCount
) {
}
