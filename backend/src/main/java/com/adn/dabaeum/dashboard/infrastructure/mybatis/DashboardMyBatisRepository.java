package com.adn.dabaeum.dashboard.infrastructure.mybatis;

import com.adn.dabaeum.dashboard.domain.DashboardRepository;
import com.adn.dabaeum.dashboard.domain.InstitutionDashboard;
import com.adn.dabaeum.dashboard.domain.PlatformDashboard;
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
    public PlatformDashboard platformDashboard(int recentMemberLimit) {
        PlatformCountsRow counts = mapper.selectPlatformCounts();
        List<PlatformDashboard.RecentMember> recentMembers =
            mapper.selectRecentMembers(recentMemberLimit).stream()
                .map(row -> new PlatformDashboard.RecentMember(
                    row.userId(), row.name(), row.role(), row.createdAt()))
                .toList();
        return new PlatformDashboard(
            new PlatformDashboard.Kpis(
                z(counts.userCount()), z(counts.instructorCount()),
                z(counts.institutionCount()), z(counts.courseCount()),
                z(counts.courseInProgressCount()), z(counts.enrollmentCount())),
            new PlatformDashboard.MemberSummary(
                z(counts.learnerCount()), z(counts.instructorCount()),
                z(counts.institutionAdminCount()), z(counts.withdrawnCount())),
            new PlatformDashboard.ApprovalSummary(
                z(counts.instructorApplicationPending()),
                z(counts.institutionApplicationPending())),
            new PlatformDashboard.CourseSummary(
                z(counts.courseCount()), z(counts.courseInProgressCount()),
                z(counts.courseRecruitingCount()), z(counts.courseCompletedCount())),
            recentMembers);
    }

    @Override
    public InstitutionDashboard institutionDashboard(UUID institutionId) {
        InstitutionCountsRow counts = mapper.selectInstitutionCounts(institutionId);
        if (counts == null) {
            return new InstitutionDashboard(0, 0, 0, 0, 0, 0);
        }
        return new InstitutionDashboard(
            z(counts.instructorTotal()), z(counts.instructorActive()),
            z(counts.instructorInactive()), z(counts.instructorWithdrawn()),
            z(counts.courseCount()), z(counts.enrollmentCount()));
    }

    private static long z(Long value) {
        return value == null ? 0 : value;
    }
}
