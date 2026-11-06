package com.adn.dabaeum.dashboard.infrastructure.mybatis;

import com.adn.dabaeum.dashboard.domain.DashboardRepository;
import com.adn.dabaeum.dashboard.domain.InstitutionDashboard;
import com.adn.dabaeum.dashboard.domain.PlatformDashboard;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile({"local", "dev"})
public class DashboardMyBatisRepository implements DashboardRepository {

    private final DashboardMapper mapper;

    public DashboardMyBatisRepository(DashboardMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public PlatformDashboard platformDashboard(int recentMemberLimit, Instant now) {
        PlatformCountsRow counts = mapper.selectPlatformCounts(now);
        List<PlatformDashboard.RecentMember> recentMembers =
            mapper.selectRecentMembers(recentMemberLimit).stream()
                .map(row -> new PlatformDashboard.RecentMember(
                    row.userId(), row.name(), row.role(), row.createdAt()))
                .toList();
        return new PlatformDashboard(
            new PlatformDashboard.Kpis(
                z(counts.userCount()), z(counts.instructorCount()),
                z(counts.institutionCount()), z(counts.courseCount()),
                z(counts.courseInProgressCount()), z(counts.enrollmentCount()),
                z(counts.completionCount()), z(counts.badgeCount())),
            new PlatformDashboard.MemberSummary(
                z(counts.learnerCount()), z(counts.instructorCount()),
                z(counts.institutionAdminCount()), z(counts.withdrawnCount()),
                z(counts.recentSignupCount())),
            new PlatformDashboard.ApprovalSummary(
                z(counts.instructorApplicationPending()),
                z(counts.institutionApplicationPending())),
            new PlatformDashboard.CourseSummary(
                z(counts.courseCount()), z(counts.courseInProgressCount()),
                z(counts.courseRecruitingCount()), z(counts.courseCompletedCount())),
            new PlatformDashboard.InstructorSummary(
                z(counts.instructorCount()), z(counts.instructorActiveCount()),
                z(counts.instructorInactiveCount()), z(counts.instructorWithdrawnCount())),
            new PlatformDashboard.InstitutionSummary(
                z(counts.institutionCount()), z(counts.institutionActiveCount()),
                z(counts.institutionInactiveCount())),
            recentMembers);
    }

    @Override
    public InstitutionDashboard institutionDashboard(UUID institutionId) {
        InstitutionCountsRow counts = mapper.selectInstitutionCounts(institutionId);
        List<InstitutionDashboard.CategoryRatio> categoryRatios = categoryRatios(institutionId);
        if (counts == null) {
            return new InstitutionDashboard(
                0, 0, 0, 0, 0, 0, 0, 0,
                new InstitutionDashboard.CourseSummary(0, 0, 0),
                categoryRatios);
        }
        return new InstitutionDashboard(
            z(counts.instructorTotal()), z(counts.instructorActive()),
            z(counts.instructorInactive()), z(counts.instructorWithdrawn()),
            z(counts.courseCount()), z(counts.enrollmentCount()),
            z(counts.approvedEnrollmentCount()), z(counts.completionCount()),
            new InstitutionDashboard.CourseSummary(
                z(counts.courseRecruiting()), z(counts.courseInProgress()),
                z(counts.courseCompleted())),
            categoryRatios);
    }

    private List<InstitutionDashboard.CategoryRatio> categoryRatios(UUID institutionId) {
        List<CategoryCountRow> rows = mapper.selectInstitutionCategoryCounts(institutionId);
        long total = rows.stream().mapToLong(row -> z(row.courseCount())).sum();
        return rows.stream()
            .map(row -> new InstitutionDashboard.CategoryRatio(
                row.category(), z(row.courseCount()), percentage(z(row.courseCount()), total)))
            .toList();
    }

    // 강좌가 0개면 나눌 수 없으니 0으로 방어한다.
    private static int percentage(long count, long total) {
        return total == 0 ? 0 : (int) Math.round(count * 100.0 / total);
    }

    private static long z(Long value) {
        return value == null ? 0 : value;
    }
}
