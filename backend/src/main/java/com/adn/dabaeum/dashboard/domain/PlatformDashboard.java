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
    InstructorSummary instructorSummary,
    InstitutionSummary institutionSummary,
    List<RecentMember> recentMembers
) {

    public record Kpis(
        long userCount,
        long instructorCount,
        long institutionCount,
        long courseCount,
        long courseInProgressCount,
        long enrollmentCount,
        // 누적 수료(지금 COMPLETED)·유효 배지(지금 ISSUED). 취소·폐기된 것은 빠진다 (QA ADM-05).
        long completionCount,
        long badgeCount
    ) {
    }

    public record MemberSummary(
        long learnerCount,
        long instructorCount,
        long institutionAdminCount,
        long withdrawnCount,
        long recentSignupCount
    ) {
    }

    // 사이트 전체 강사 통계 (스펙 #43). 한 강사가 여러 기관에 소속돼도 한 번만 센다.
    public record InstructorSummary(
        long total,
        long active,
        long inactive,
        long withdrawn
    ) {
    }

    // 기관 상태별 집계 (스펙 #39).
    public record InstitutionSummary(
        long total,
        long active,
        long inactive
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
