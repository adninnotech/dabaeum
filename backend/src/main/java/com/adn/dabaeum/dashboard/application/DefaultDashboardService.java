package com.adn.dabaeum.dashboard.application;

import com.adn.dabaeum.authorization.application.AuthorizationPolicy;
import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import com.adn.dabaeum.dashboard.domain.DashboardRepository;
import com.adn.dabaeum.dashboard.domain.InstitutionDashboard;
import com.adn.dabaeum.dashboard.domain.PlatformDashboard;
import java.time.Clock;
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
    private final Clock clock;

    public DefaultDashboardService(
        DashboardRepository dashboardRepository,
        AuthorizationPolicy authorizationPolicy,
        Clock clock
    ) {
        this.dashboardRepository = dashboardRepository;
        this.authorizationPolicy = authorizationPolicy;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public PlatformDashboard platformDashboard(AuthenticatedUserContext actor) {
        authorizationPolicy.requirePlatformAdmin(actor);
        // "최근 가입" 30일 기준을 Clock 에서 얻어 SQL 의 CURRENT_TIMESTAMP 대신 넘긴다 —
        // 테스트가 고정 시각으로 검증할 수 있도록.
        return dashboardRepository.platformDashboard(RECENT_MEMBER_LIMIT, clock.instant());
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
