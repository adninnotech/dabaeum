package com.adn.dabaeum.dashboard.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.adn.dabaeum.authorization.application.AuthorizationPolicy;
import com.adn.dabaeum.common.security.AuthenticatedRole;
import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import com.adn.dabaeum.dashboard.domain.DashboardRepository;
import com.adn.dabaeum.dashboard.domain.PlatformDashboard;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** "최근 가입" 30일 기준이 SQL 의 CURRENT_TIMESTAMP 가 아니라 Clock 에서 온다는 것을 확인한다. */
class DefaultDashboardServiceTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-09-18T00:00:00Z");

    private final DashboardRepository dashboardRepository = mock(DashboardRepository.class);
    private final AuthorizationPolicy authorizationPolicy = new AuthorizationPolicy();
    private final Clock clock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
    private final DefaultDashboardService service =
        new DefaultDashboardService(dashboardRepository, authorizationPolicy, clock);

    @Test
    void 고정된_Clock_이_저장소에_now_로_전달된다() {
        when(dashboardRepository.platformDashboard(anyInt(), any(Instant.class)))
            .thenReturn(stubDashboard());

        PlatformDashboard dashboard = service.platformDashboard(platformAdmin());

        ArgumentCaptor<Instant> nowCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(dashboardRepository).platformDashboard(anyInt(), nowCaptor.capture());
        assertThat(nowCaptor.getValue()).isEqualTo(FIXED_NOW);
        assertThat(dashboard.memberSummary().recentSignupCount()).isEqualTo(3);
    }

    private PlatformDashboard stubDashboard() {
        return new PlatformDashboard(
            new PlatformDashboard.Kpis(0, 0, 0, 0, 0, 0, 0, 0),
            new PlatformDashboard.MemberSummary(0, 0, 0, 0, 3),
            new PlatformDashboard.ApprovalSummary(0, 0),
            new PlatformDashboard.CourseSummary(0, 0, 0, 0),
            new PlatformDashboard.InstructorSummary(0, 0, 0, 0),
            new PlatformDashboard.InstitutionSummary(0, 0, 0),
            List.of());
    }

    private AuthenticatedUserContext platformAdmin() {
        return new AuthenticatedUserContext(UUID.randomUUID(), "LOCAL",
            Set.of(new AuthenticatedRole("PLATFORM_ADMIN", null)));
    }
}
