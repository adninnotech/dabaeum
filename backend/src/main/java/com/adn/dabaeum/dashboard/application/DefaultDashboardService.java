package com.adn.dabaeum.dashboard.application;

import com.adn.dabaeum.authorization.application.AuthorizationPolicy;
import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import com.adn.dabaeum.dashboard.domain.DashboardRepository;
import com.adn.dabaeum.dashboard.domain.InstitutionDashboard;
import com.adn.dabaeum.dashboard.domain.PlatformDashboard;
import java.util.Objects;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile({"local", "dev"})
public class DefaultDashboardService implements DashboardService {

    private static final int RECENT_MEMBER_LIMIT = 5;

    private final DashboardRepository dashboardRepository;
    private final AuthorizationPolicy authorizationPolicy;

    public DefaultDashboardService(
        DashboardRepository dashboardRepository,
        AuthorizationPolicy authorizationPolicy
    ) {
        this.dashboardRepository = dashboardRepository;
        this.authorizationPolicy = authorizationPolicy;
    }

    @Override
    @Transactional(readOnly = true)
    public PlatformDashboard platformDashboard(AuthenticatedUserContext actor) {
        authorizationPolicy.requirePlatformAdmin(actor);
        return dashboardRepository.platformDashboard(RECENT_MEMBER_LIMIT);
    }

    @Override
    @Transactional(readOnly = true)
    public InstitutionDashboard institutionDashboard(
        AuthenticatedUserContext actor, UUID institutionId
    ) {
        Objects.requireNonNull(actor, "actor");
        authorizationPolicy.requireCourseManager(actor, institutionId);
        return dashboardRepository.institutionDashboard(institutionId);
    }
}
