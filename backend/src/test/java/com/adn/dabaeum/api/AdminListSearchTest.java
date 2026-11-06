package com.adn.dabaeum.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import com.adn.dabaeum.course.api.CourseApiMapper;
import com.adn.dabaeum.course.api.CourseController;
import com.adn.dabaeum.course.api.CourseQueryApiMapper;
import com.adn.dabaeum.course.api.CourseQueryController;
import com.adn.dabaeum.course.application.CourseApplicationService;
import com.adn.dabaeum.course.application.CoursePage;
import com.adn.dabaeum.course.application.CourseQueryService;
import com.adn.dabaeum.course.application.InstitutionInstructorPage;
import com.adn.dabaeum.course.application.ListCoursesQuery;
import com.adn.dabaeum.course.domain.Course;
import com.adn.dabaeum.course.domain.CourseEducationType;
import com.adn.dabaeum.course.domain.CourseSelectionMethod;
import com.adn.dabaeum.course.domain.CourseStatus;
import com.adn.dabaeum.course.domain.CourseView;
import com.adn.dabaeum.course.domain.InstitutionInstructorView;
import com.adn.dabaeum.institution.api.InstitutionApiMapper;
import com.adn.dabaeum.institution.api.InstitutionController;
import com.adn.dabaeum.institution.application.InstitutionApplicationService;
import com.adn.dabaeum.institution.application.InstitutionPage;
import com.adn.dabaeum.institution.application.ListInstitutionsQuery;
import com.adn.dabaeum.institution.domain.Institution;
import com.adn.dabaeum.institution.domain.InstitutionListItem;
import com.adn.dabaeum.institution.domain.InstitutionStatus;
import com.adn.dabaeum.role.domain.UserRole;
import com.adn.dabaeum.support.api.SupportApiMapper;
import com.adn.dabaeum.support.api.SupportController;
import com.adn.dabaeum.support.application.NoticePage;
import com.adn.dabaeum.support.application.SupportApplicationService;
import com.adn.dabaeum.support.domain.Notice;
import com.adn.dabaeum.support.domain.NoticeAudience;
import com.adn.dabaeum.support.domain.NoticeStatus;
import com.adn.dabaeum.user.api.UserApiMapper;
import com.adn.dabaeum.user.api.UserController;
import com.adn.dabaeum.user.application.ListUsersQuery;
import com.adn.dabaeum.user.application.UserApplicationService;
import com.adn.dabaeum.user.application.UserPage;
import com.adn.dabaeum.user.application.UserView;
import com.adn.dabaeum.user.domain.User;
import com.adn.dabaeum.user.domain.UserStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 관리자 목록 4종(회원·과정·기관·공지)과 기관 소속 강사 명단이 q 검색·셀렉트 필터를 받아
 * 계약대로 응답하는지, 그리고 KeywordQuery 로 정규화된 값과 필터가 서비스로 그대로
 * 전달되는지 본다. 서비스는 mock 이라 실제 LIKE/ESCAPE·EXISTS 동작은 각 도메인의
 * 매퍼 통합 테스트가 검증한다.
 */
@WebMvcTest({
    UserController.class,
    InstitutionController.class,
    SupportController.class,
    CourseController.class,
    CourseQueryController.class
})
@ActiveProfiles("local")
@TestPropertySource(properties = "dabaeum.security.dev.bearer-token=admin-search-token")
@Import({
    com.adn.dabaeum.authorization.application.AuthorizationPolicy.class,
    UserApiMapper.class,
    InstitutionApiMapper.class,
    SupportApiMapper.class,
    CourseApiMapper.class,
    CourseQueryApiMapper.class,
    ApiExceptionHandler.class,
    ClockConfiguration.class,
    SecurityConfiguration.class,
    DevBearerSecurityConfiguration.class,
    LocalDevSecuritySupportConfiguration.class,
    JacksonConfiguration.class,
    RequestIdFilter.class
})
class AdminListSearchTest {

    private static final UUID INSTITUTION_ID = UUID.fromString(
        "30000000-0000-0000-0000-000000000001");
    private static final UUID USER_ID = UUID.fromString(
        "30000000-0000-0000-0000-000000000002");

    @Autowired MockMvc mockMvc;
    @MockitoBean UserApplicationService userApplicationService;
    @MockitoBean InstitutionApplicationService institutionApplicationService;
    @MockitoBean SupportApplicationService supportApplicationService;
    @MockitoBean CourseApplicationService courseApplicationService;
    @MockitoBean CourseQueryService courseQueryService;
    @MockitoBean CurrentUserProvider currentUserProvider;

    @BeforeEach
    void setUp() {
        lenient().when(currentUserProvider.requireContext()).thenReturn(platformAdmin());
        lenient().when(userApplicationService.list(any()))
            .thenReturn(new UserPage(
                List.of(new UserView(sampleUser(), List.of())), 0, 20, 1, 1));
        lenient().when(institutionApplicationService.list(any()))
            .thenReturn(new InstitutionPage(
                List.of(new InstitutionListItem(sampleInstitution(), 0, 0, 0, 0, null)), 0, 20, 1, 1));
        lenient().when(supportApplicationService.listAdminNotices(
                any(), anyInt(), anyInt(), any(), any(), any()))
            .thenReturn(new NoticePage(List.of(sampleNotice()), 0, 20, 1, 1));
        lenient().when(courseApplicationService.list(any(), any()))
            .thenReturn(new CoursePage(List.of(new CourseView(sampleCourse(), 0, null, null)), 0, 20, 1, 1));
        lenient().when(courseQueryService.listInstitutionInstructors(
                any(), any(), any(), any(), anyInt(), anyInt(), any()))
            .thenReturn(new InstitutionInstructorPage(
                List.of(sampleInstructorView()), 0, 20, 1, 1));
    }

    @Test
    void 네_목록이_모두_q_를_받는다() throws Exception {
        for (String path : List.of(
                "/api/v1/users", "/api/v1/courses", "/api/v1/institutions",
                "/api/v1/admin/notices")) {
            mockMvc.perform(get(path).param("q", "테스트").with(user("admin").roles("PLATFORM_ADMIN")))
                .andExpect(status().isOk());
        }
    }

    @Test
    void 기관_소속_강사_명단도_q_를_받는다() throws Exception {
        mockMvc.perform(get("/api/v1/institutions/{id}/instructors", INSTITUTION_ID)
                .param("q", "김강사")
                .with(user("manager").roles("INSTITUTION_ADMIN")))
            .andExpect(status().isOk());
    }

    @Test
    void 회원_목록은_정규화된_키워드와_역할을_서비스로_전달한다() throws Exception {
        mockMvc.perform(get("/api/v1/users")
                .param("q", "  seed.learner  ").param("role", "INSTRUCTOR")
                .with(user("admin").roles("PLATFORM_ADMIN")))
            .andExpect(status().isOk());

        ArgumentCaptor<ListUsersQuery> captor = ArgumentCaptor.forClass(ListUsersQuery.class);
        verify(userApplicationService).list(captor.capture());
        assertThat(captor.getValue().keyword()).isEqualTo("seed.learner");
        assertThat(captor.getValue().role()).isEqualTo(UserRole.INSTRUCTOR);
    }

    @Test
    void 기관_목록은_키워드와_상태를_서비스로_전달한다() throws Exception {
        mockMvc.perform(get("/api/v1/institutions")
                .param("q", "다배움").param("status", "ACTIVE")
                .param("region", " 경기 포천시 ")
                .with(user("admin").roles("PLATFORM_ADMIN")))
            .andExpect(status().isOk());

        ArgumentCaptor<ListInstitutionsQuery> captor =
            ArgumentCaptor.forClass(ListInstitutionsQuery.class);
        verify(institutionApplicationService).list(captor.capture());
        assertThat(captor.getValue().keyword()).isEqualTo("다배움");
        assertThat(captor.getValue().status()).isEqualTo(InstitutionStatus.ACTIVE);
    }

    @Test
    void 과정_목록은_교육유형과_선별방식을_서비스로_전달한다() throws Exception {
        mockMvc.perform(get("/api/v1/courses")
                .param("q", "데이터").param("educationType", "OFFLINE")
                .param("selectionMethod", "LOTTERY").param("status", "RECRUITING")
                .with(user("admin").roles("PLATFORM_ADMIN")))
            .andExpect(status().isOk());

        ArgumentCaptor<ListCoursesQuery> captor = ArgumentCaptor.forClass(ListCoursesQuery.class);
        verify(courseApplicationService).list(captor.capture(), any());
        assertThat(captor.getValue().educationType()).isEqualTo(CourseEducationType.OFFLINE);
        assertThat(captor.getValue().selectionMethod()).isEqualTo(CourseSelectionMethod.LOTTERY);
        assertThat(captor.getValue().status()).isEqualTo(CourseStatus.RECRUITING);
    }

    @Test
    void 공지_관리_목록은_키워드와_대상과_상태를_서비스로_전달한다() throws Exception {
        mockMvc.perform(get("/api/v1/admin/notices")
                .param("q", "점검").param("audience", "INSTRUCTOR").param("status", "DRAFT")
                .with(user("admin").roles("PLATFORM_ADMIN")))
            .andExpect(status().isOk());

        verify(supportApplicationService).listAdminNotices(
            any(), eq(0), eq(20), eq("점검"), eq(NoticeAudience.INSTRUCTOR), eq(NoticeStatus.DRAFT));
    }

    @Test
    void 기관_소속_강사_명단은_키워드를_서비스로_전달한다() throws Exception {
        mockMvc.perform(get("/api/v1/institutions/{id}/instructors", INSTITUTION_ID)
                .param("q", "김강사")
                .with(user("manager").roles("INSTITUTION_ADMIN")))
            .andExpect(status().isOk());

        verify(courseQueryService).listInstitutionInstructors(
            any(), eq(INSTITUTION_ID), any(), eq("김강사"), anyInt(), anyInt(), any());
    }

    @Test
    void q_가_100자를_넘으면_422() throws Exception {
        String tooLong = "가".repeat(101);
        mockMvc.perform(get("/api/v1/users")
                .param("q", tooLong).with(user("admin").roles("PLATFORM_ADMIN")))
            .andExpect(status().isUnprocessableEntity());
        mockMvc.perform(get("/api/v1/courses")
                .param("q", tooLong).with(user("admin").roles("PLATFORM_ADMIN")))
            .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void 알수없는_셀렉트값은_400() throws Exception {
        mockMvc.perform(get("/api/v1/users")
                .param("status", "NOPE").with(user("admin").roles("PLATFORM_ADMIN")))
            .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/users")
                .param("role", "NOPE").with(user("admin").roles("PLATFORM_ADMIN")))
            .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/institutions")
                .param("status", "NOPE").with(user("admin").roles("PLATFORM_ADMIN")))
            .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/courses")
                .param("educationType", "NOPE").with(user("admin").roles("PLATFORM_ADMIN")))
            .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/courses")
                .param("status", "NOPE").with(user("admin").roles("PLATFORM_ADMIN")))
            .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/admin/notices")
                .param("audience", "NOPE").with(user("admin").roles("PLATFORM_ADMIN")))
            .andExpect(status().isBadRequest());
    }

    @Test
    void 관리자가_아니면_회원_목록을_볼_수_없다() throws Exception {
        mockMvc.perform(get("/api/v1/users").param("q", "seed")
                .with(user("learner").roles("LEARNER")))
            .andExpect(status().isForbidden());
    }

    private AuthenticatedUserContext platformAdmin() {
        return new AuthenticatedUserContext(USER_ID, "LOCAL",
            Set.of(new AuthenticatedRole("PLATFORM_ADMIN", null)));
    }

    private User sampleUser() {
        Instant instant = Instant.parse("2026-08-04T00:00:00Z").truncatedTo(ChronoUnit.MICROS);
        return new User(
            USER_ID, "다배움 사용자", "seed.learner@dabaeum.kr", "010-1234-5678",
            LocalDate.of(2000, 1, 2), UserStatus.ACTIVE, null, instant, instant);
    }

    private Institution sampleInstitution() {
        Instant instant = Instant.parse("2026-08-04T00:00:00Z").truncatedTo(ChronoUnit.MICROS);
        return new Institution(
            INSTITUTION_ID, "INST-001", "다배움 평생교육원", "123-45-67890", "대표자",
            "대구광역시", "053-000-0000", "inst@example.com", InstitutionStatus.ACTIVE,
            instant, instant, null);
    }

    private Notice sampleNotice() {
        Instant instant = Instant.parse("2026-08-04T00:00:00Z").truncatedTo(ChronoUnit.MICROS);
        return new Notice(
            UUID.randomUUID(), "서비스 점검 안내", "본문", NoticeAudience.INSTRUCTOR,
            NoticeStatus.DRAFT, null, USER_ID, "개발 관리자", instant, instant);
    }

    private Course sampleCourse() {
        Instant instant = Instant.parse("2026-08-04T00:00:00Z").truncatedTo(ChronoUnit.MICROS);
        return new Course(
            UUID.randomUUID(), INSTITUTION_ID, "COURSE-001", "과정 제목", "상세 설명", "TSIX_040201",
            CourseEducationType.HYBRID,
            LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 31),
            LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 25),
            30, "서울 교육장", "https://example.test/course", true, new BigDecimal("12.50"),
            CourseStatus.RECRUITMENT_CLOSED, instant, instant, null);
    }

    private InstitutionInstructorView sampleInstructorView() {
        Instant instant = Instant.parse("2026-08-04T00:00:00Z").truncatedTo(ChronoUnit.MICROS);
        return new InstitutionInstructorView(
            USER_ID, "김강사", "instructor@example.com", "010-1234-5678", "ACTIVE",
            instant, 3, null, null, null);
    }
}
