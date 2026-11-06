package com.adn.dabaeum.dashboard.api;

import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.adn.dabaeum.common.api.ApiExceptionHandler;
import com.adn.dabaeum.common.config.ClockConfiguration;
import com.adn.dabaeum.common.config.DevBearerSecurityConfiguration;
import com.adn.dabaeum.common.config.LocalDevSecuritySupportConfiguration;
import com.adn.dabaeum.common.config.JacksonConfiguration;
import com.adn.dabaeum.common.config.SecurityConfiguration;
import com.adn.dabaeum.common.security.AuthenticatedRole;
import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import com.adn.dabaeum.common.security.CurrentUserProvider;
import com.adn.dabaeum.common.web.RequestIdFilter;
import com.adn.dabaeum.dashboard.application.DashboardService;
import com.adn.dabaeum.dashboard.domain.PlatformDashboard;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** 스펙 #43(사이트 전체 강사 통계)·#39(기관 상태별 집계)·최근 가입 30일. */
@WebMvcTest(DashboardController.class)
@ActiveProfiles("local")
@TestPropertySource(properties = "dabaeum.security.dev.bearer-token=platform-dashboard-token")
@Import({
    ApiExceptionHandler.class,
    ClockConfiguration.class,
    SecurityConfiguration.class,
    DevBearerSecurityConfiguration.class,
    LocalDevSecuritySupportConfiguration.class,
    JacksonConfiguration.class,
    RequestIdFilter.class
})
class PlatformDashboardTest {

    private static final UUID ADMIN_ID = UUID.fromString("a1000000-0000-0000-0000-000000000001");
    private static final Instant NOW = Instant.parse("2026-09-18T00:00:00Z");

    @Autowired MockMvc mockMvc;
    @MockitoBean DashboardService dashboardService;
    @MockitoBean CurrentUserProvider currentUserProvider;

    @Test
    void 강사_회원_기관_통계를_한번에_준다() throws Exception {
        lenient().when(currentUserProvider.requireContext()).thenReturn(admin());
        when(dashboardService.platformDashboard(org.mockito.ArgumentMatchers.any()))
            .thenReturn(dashboard());

        mockMvc.perform(get("/api/v1/admin/dashboard")
                .with(user("admin").roles("PLATFORM_ADMIN")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.kpis.completionCount").value(60))
            .andExpect(jsonPath("$.data.kpis.badgeCount").value(25))
            .andExpect(jsonPath("$.data.instructorSummary.total").isNumber())
            .andExpect(jsonPath("$.data.instructorSummary.active").isNumber())
            .andExpect(jsonPath("$.data.instructorSummary.inactive").isNumber())
            .andExpect(jsonPath("$.data.instructorSummary.withdrawn").isNumber())
            .andExpect(jsonPath("$.data.memberSummary.recentSignupCount").isNumber())
            .andExpect(jsonPath("$.data.institutionSummary.total").isNumber())
            .andExpect(jsonPath("$.data.institutionSummary.active").isNumber())
            .andExpect(jsonPath("$.data.institutionSummary.inactive").isNumber());
    }

    @Test
    void 학습자는_전체_대시보드를_볼_수_없다() throws Exception {
        mockMvc.perform(get("/api/v1/admin/dashboard")
                .with(user("learner").roles("LEARNER")))
            .andExpect(status().isForbidden());
    }

    private PlatformDashboard dashboard() {
        return new PlatformDashboard(
            new PlatformDashboard.Kpis(100, 10, 5, 50, 20, 200, 60, 25),
            new PlatformDashboard.MemberSummary(80, 10, 5, 5, 3),
            new PlatformDashboard.ApprovalSummary(2, 1),
            new PlatformDashboard.CourseSummary(50, 20, 15, 15),
            new PlatformDashboard.InstructorSummary(10, 7, 2, 1),
            new PlatformDashboard.InstitutionSummary(5, 4, 1),
            List.of(new PlatformDashboard.RecentMember(
                UUID.randomUUID(), "홍길동", "LEARNER", NOW)));
    }

    private AuthenticatedUserContext admin() {
        return new AuthenticatedUserContext(ADMIN_ID, "LOCAL",
            Set.of(new AuthenticatedRole("PLATFORM_ADMIN", null)));
    }
}
