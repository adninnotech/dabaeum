package com.adn.dabaeum.dashboard.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record
PlatformDashboard(
    Kpis kpis,
    MemberSummary memberSummary,
    ApprovalSummary approvalSummary,
    CourseSummary courseSummary,
    List<RecentMember> recentMembers
) {

    public record Kpis(
        long userCount,
        long instructorCount,
        long institutionCount,
        long courseCount,
        long courseInProgressCount,
        long enrollmentCount
    ) {
    }

    public record MemberSummary(
        long learnerCount,
        long instructorCount,
        long institutionAdminCount,
        long withdrawnCount
    ) {
    }

    public record ApprovalSummary(
        long instructorApplicationPending,
        long institutionApplicationPending
    ) {
    }

    public record CourseSummary(
        long total,
        long inProgress,
        long recruiting,
        long completed
    ) {
    }

    public record RecentMember(
        UUID userId,
        String name,
        String role,
        Instant createdAt
    ) {
    }
}
