package com.adn.dabaeum.dashboard.domain;

import java.util.UUID;

public interface DashboardRepository {

    PlatformDashboard platformDashboard(int recentMemberLimit);

    InstitutionDashboard institutionDashboard(UUID institutionId);
}
