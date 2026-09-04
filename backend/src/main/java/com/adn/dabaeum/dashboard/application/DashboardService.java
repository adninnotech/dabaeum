package com.adn.dabaeum.dashboard.application;

import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import com.adn.dabaeum.dashboard.domain.InstitutionDashboard;
import com.adn.dabaeum.dashboard.domain.PlatformDashboard;
import java.util.UUID;

public interface DashboardService {

    PlatformDashboard platformDashboard(AuthenticatedUserContext actor);

    InstitutionDashboard institutionDashboard(
        AuthenticatedUserContext actor, UUID institutionId);
}
