package com.adn.dabaeum.dashboard.domain;

import java.util.List;

public record InstitutionDashboard(
    long instructorTotal,
    long instructorActive,
    long instructorInactive,
    long instructorWithdrawn,
    long courseCount,
    long enrollmentCount,
    // 지금 승인 상태인 수강(철회·취소 제외)과 지금 COMPLETED 인 수료. 누적이다 (QA DATA-01·DATA-02·INST-06).
    long approvedEnrollmentCount,
    long completionCount,
    CourseSummary courseSummary,
    List<CategoryRatio> categoryRatios
) {

    public record CourseSummary(
        long recruiting,
        long inProgress,
        long completed
    ) {
    }

    public record CategoryRatio(
        String category,
        long courseCount,
        int percentage
    ) {
    }
}
