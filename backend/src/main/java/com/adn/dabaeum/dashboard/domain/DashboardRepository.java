package com.adn.dabaeum.dashboard.domain;

import java.time.Instant;
import java.util.UUID;

public interface DashboardRepository {

    PlatformDashboard platformDashboard(int recentMemberLimit, Instant now);

    InstitutionDashboard institutionDashboard(UUID institutionId);
}
