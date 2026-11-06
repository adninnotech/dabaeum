package com.adn.dabaeum.institution.api;

import static org.mockito.ArgumentMatchers.any;
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
import com.adn.dabaeum.dashboard.api.DashboardController;
import com.adn.dabaeum.dashboard.application.DashboardService;
import com.adn.dabaeum.dashboard.domain.InstitutionDashboard;
import com.adn.dabaeum.institution.application.InstitutionApplicationService;
import com.adn.dabaeum.institution.application.InstitutionPage;
import com.adn.dabaeum.institution.domain.Institution;
import com.adn.dabaeum.institution.domain.InstitutionAdministrator;
import com.adn.dabaeum.institution.domain.InstitutionListItem;
import com.adn.dabaeum.institution.domain.InstitutionStatus;
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

/**
 * B-5 강사 그룹 상세·대시보드. 기관 상세는 홈페이지·지역·관리자 목록을 주고, 목록은
 * administrators 를 아예 빼서 N+1 을 막는다. 대시보드는 강좌 상태별 분해와
 * 카테고리 비율을 더 준다 — 강좌가 0개일 때 0으로 나누지 않는지가 핵심이다.
 */
@WebMvcTest(controllers = {InstitutionController.class, DashboardController.class})
@ActiveProfiles("local")
@TestPropertySource(properties =
    "dabaeum.security.dev.bearer-token=institution-dashboard-test-token")
@Import({
    InstitutionApiMapper.class,
    com.adn.dabaeum.authorization.application.AuthorizationPolicy.class,
    ApiExceptionHandler.class,
    ClockConfiguration.class,
    SecurityConfiguration.class,
    DevBearerSecurityConfiguration.class,
    LocalDevSecuritySupportConfiguration.class,
    JacksonConfiguration.class,
    RequestIdFilter.class
})
class InstitutionDashboardTest {

    private static final UUID INSTITUTION_ID =
        UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID ADMIN_ID =
        UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    InstitutionApplicationService institutionService;

    @MockitoBean
    DashboardService dashboardService;

    @MockitoBean
    CurrentUserProvider currentUserProvider;

    @Test
    void 기관_상세가_홈페이지와_지역을_준다() throws Exception {
        when(institutionService.get(INSTITUTION_ID)).thenReturn(institution());
        lenient().when(institutionService.administrators(INSTITUTION_ID))
            .thenReturn(List.of());
        lenient().when(currentUserProvider.requireContext()).thenReturn(platformAdmin());

        mockMvc.perform(get("/api/v1/institutions/{id}", INSTITUTION_ID)
                .with(user("platform-admin").roles("PLATFORM_ADMIN")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.homepageUrl").value("https://example.kr/edu"))
            .andExpect(jsonPath("$.data.region").value("경기 어딘가시"));
    }

    @Test
    void 기관_상세가_관리자명을_준다() throws Exception {
        when(institutionService.get(INSTITUTION_ID)).thenReturn(institution());
        when(institutionService.administrators(INSTITUTION_ID))
            .thenReturn(List.of(new InstitutionAdministrator(
                ADMIN_ID, "김관리", "admin@example.kr", "010-0000-0000")));
        when(currentUserProvider.requireContext()).thenReturn(platformAdmin());

        mockMvc.perform(get("/api/v1/institutions/{id}", INSTITUTION_ID)
                .with(user("platform-admin").roles("PLATFORM_ADMIN")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.administrators[0].name").value("김관리"))
            .andExpect(jsonPath("$.data.administrators[0].email").value("admin@example.kr"));
    }

    @Test
    void 학습자에게는_기관_관리자_연락처가_나가지_않는다() throws Exception {
        // 이 화면은 인증만 하면 누구나 연다. 관리자 이름·이메일·전화가 그대로 나가면
        // 가입만 해도 전 기관 담당자 연락처를 긁을 수 있다.
        when(institutionService.get(INSTITUTION_ID)).thenReturn(institution());
        lenient().when(institutionService.administrators(INSTITUTION_ID))
            .thenReturn(List.of(new InstitutionAdministrator(
                ADMIN_ID, "김관리", "admin@example.kr", "010-0000-0000")));
        when(currentUserProvider.requireContext()).thenReturn(
            new AuthenticatedUserContext(ADMIN_ID, "LOCAL",
                Set.of(new AuthenticatedRole("LEARNER", null))));

        mockMvc.perform(get("/api/v1/institutions/{id}", INSTITUTION_ID)
                .with(user("learner").roles("LEARNER")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.administrators").isEmpty());
    }

    @Test
    void 다른_기관_관리자에게도_나가지_않는다() throws Exception {
        when(institutionService.get(INSTITUTION_ID)).thenReturn(institution());
        lenient().when(institutionService.administrators(INSTITUTION_ID))
            .thenReturn(List.of(new InstitutionAdministrator(
                ADMIN_ID, "김관리", "admin@example.kr", "010-0000-0000")));
        when(currentUserProvider.requireContext()).thenReturn(
            new AuthenticatedUserContext(ADMIN_ID, "LOCAL",
                Set.of(new AuthenticatedRole("INSTITUTION_ADMIN", UUID.randomUUID()))));

        mockMvc.perform(get("/api/v1/institutions/{id}", INSTITUTION_ID)
                .with(user("other-admin").roles("INSTITUTION_ADMIN")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.administrators").isEmpty());
    }

    private AuthenticatedUserContext platformAdmin() {
        return new AuthenticatedUserContext(ADMIN_ID, "LOCAL",
            Set.of(new AuthenticatedRole("PLATFORM_ADMIN", null)));
    }

    @Test
    void 기관_목록에는_administrators_가_없다() throws Exception {
        lenient().when(institutionService.list(any())).thenReturn(new InstitutionPage(
            List.of(new InstitutionListItem(institution(), 0, 0, 0, 0, null)), 0, 20, 1, 1));

        mockMvc.perform(get("/api/v1/institutions")
                .with(user("platform-admin").roles("PLATFORM_ADMIN")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].administrators").doesNotExist());
    }

    @Test
    void 기관_목록에는_집계_3종이_실린다() throws Exception {
        lenient().when(institutionService.list(any())).thenReturn(new InstitutionPage(
            List.of(new InstitutionListItem(institution(), 5, 3, 12, 2, "김다혜")), 0, 20, 1, 1));

        mockMvc.perform(get("/api/v1/institutions")
                .with(user("platform-admin").roles("PLATFORM_ADMIN")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].instructorCount").value(5))
            .andExpect(jsonPath("$.data[0].courseCount").value(3))
            .andExpect(jsonPath("$.data[0].learnerCount").value(12))
            .andExpect(jsonPath("$.data[0].adminCount").value(2))
            .andExpect(jsonPath("$.data[0].primaryAdminName").value("김다혜"));
    }

    @Test
    void 기관_상세에는_집계_3종_키가_없다() throws Exception {
        // 상세 경로(findActiveById)는 집계 3종 컬럼을 select 하지 않으므로 항상 null 이다
        // — institution() 그대로가 그 상태를 대표한다.
        when(institutionService.get(INSTITUTION_ID))
            .thenReturn(institution());
        lenient().when(institutionService.administrators(INSTITUTION_ID))
            .thenReturn(List.of());
        lenient().when(currentUserProvider.requireContext()).thenReturn(platformAdmin());

        mockMvc.perform(get("/api/v1/institutions/{id}", INSTITUTION_ID)
                .with(user("platform-admin").roles("PLATFORM_ADMIN")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.instructorCount").doesNotExist())
            .andExpect(jsonPath("$.data.courseCount").doesNotExist())
            .andExpect(jsonPath("$.data.learnerCount").doesNotExist())
            .andExpect(jsonPath("$.data.adminCount").doesNotExist())
            .andExpect(jsonPath("$.data.primaryAdminName").doesNotExist());
    }

    @Test
    void 강사도_과정도_수강생도_없으면_목록에서_0이다() throws Exception {
        lenient().when(institutionService.list(any())).thenReturn(new InstitutionPage(
            List.of(new InstitutionListItem(institution(), 0, 0, 0, 0, null)), 0, 20, 1, 1));

        mockMvc.perform(get("/api/v1/institutions")
                .with(user("platform-admin").roles("PLATFORM_ADMIN")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].instructorCount").value(0))
            .andExpect(jsonPath("$.data[0].courseCount").value(0))
            .andExpect(jsonPath("$.data[0].learnerCount").value(0));
    }

    @Test
    void 대시보드가_과정_상태별_분해와_카테고리_비율을_준다() throws Exception {
        AuthenticatedUserContext context = new AuthenticatedUserContext(
            ADMIN_ID, "LOCAL",
            Set.of(new AuthenticatedRole("INSTITUTION_ADMIN", INSTITUTION_ID)));
        when(currentUserProvider.requireContext()).thenReturn(context);
        when(dashboardService.institutionDashboard(context, INSTITUTION_ID)).thenReturn(
            new InstitutionDashboard(
                10, 8, 1, 1, 5, 42, 30, 12,
                new InstitutionDashboard.CourseSummary(2, 1, 2),
                List.of(
                    new InstitutionDashboard.CategoryRatio("인문교양", 3, 60),
                    new InstitutionDashboard.CategoryRatio("IT", 2, 40))));

        mockMvc.perform(get("/api/v1/institutions/{institutionId}/dashboard", INSTITUTION_ID)
                .with(user("institution-admin").roles("INSTITUTION_ADMIN")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.approvedEnrollmentCount").value(30))
            .andExpect(jsonPath("$.data.completionCount").value(12))
            .andExpect(jsonPath("$.data.courseSummary.recruiting").value(2))
            .andExpect(jsonPath("$.data.courseSummary.inProgress").value(1))
            .andExpect(jsonPath("$.data.courseSummary.completed").value(2))
            .andExpect(jsonPath("$.data.categoryRatios[0].category").value("인문교양"))
            .andExpect(jsonPath("$.data.categoryRatios[0].courseCount").value(3))
            .andExpect(jsonPath("$.data.categoryRatios[0].percentage").value(60));
    }

    @Test
    void 강좌가_없으면_카테고리_비율은_빈_배열이다() throws Exception {
        AuthenticatedUserContext context = new AuthenticatedUserContext(
            ADMIN_ID, "LOCAL",
            Set.of(new AuthenticatedRole("PLATFORM_ADMIN", null)));
        when(currentUserProvider.requireContext()).thenReturn(context);
        when(dashboardService.institutionDashboard(context, INSTITUTION_ID)).thenReturn(
            new InstitutionDashboard(
                0, 0, 0, 0, 0, 0, 0, 0,
                new InstitutionDashboard.CourseSummary(0, 0, 0),
                List.of()));

        mockMvc.perform(get("/api/v1/institutions/{institutionId}/dashboard", INSTITUTION_ID)
                .with(user("platform-admin").roles("PLATFORM_ADMIN")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.categoryRatios").isEmpty());
    }

    private Institution institution() {
        Instant now = Instant.parse("2026-08-01T00:00:00Z");
        return new Institution(
            INSTITUTION_ID, "INST-DASH-001", "대시보드 테스트 기관", "123-45-67890",
            "대표자", "주소", "02-0000-0000", "inst@example.kr", InstitutionStatus.ACTIVE,
            now, now, null,
            "https://example.kr/edu", "경기 어딘가시", null);
    }
}
