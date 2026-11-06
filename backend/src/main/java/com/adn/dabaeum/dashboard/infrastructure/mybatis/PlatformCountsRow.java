package com.adn.dabaeum.dashboard.infrastructure.mybatis;

public record PlatformCountsRow(
    Long userCount,
    Long instructorCount,
    Long institutionCount,
    Long courseCount,
    Long courseInProgressCount,
    Long courseRecruitingCount,
    Long courseCompletedCount,
    Long enrollmentCount,
    Long learnerCount,
    Long institutionAdminCount,
    Long withdrawnCount,
    Long instructorApplicationPending,
    Long institutionApplicationPending,
    Long instructorActiveCount,
    Long instructorInactiveCount,
    Long instructorWithdrawnCount,
    Long recentSignupCount,
    Long institutionActiveCount,
    Long institutionInactiveCount,
    Long completionCount,
    Long badgeCount
) {
}
