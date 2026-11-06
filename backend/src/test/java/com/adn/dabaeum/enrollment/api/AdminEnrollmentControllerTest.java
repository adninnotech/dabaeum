package com.adn.dabaeum.enrollment.api;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.everyItem;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
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
import com.adn.dabaeum.common.web.RequestIdFilter;
import com.adn.dabaeum.course.domain.CourseEducationType;
import com.adn.dabaeum.enrollment.application.EnrollmentQueryService;
import com.adn.dabaeum.enrollment.application.InstitutionEnrollmentPage;
import com.adn.dabaeum.enrollment.domain.EnrollmentStatus;
import com.adn.dabaeum.enrollment.domain.InstitutionEnrollmentView;
import java.time.Instant;
import java.util.List;
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
 * 스펙 #42 · 수강현황관리 화면의 사이트 전체 수강신청 목록.
 *
 * <p>{@code /admin/*} 은 PLATFORM_ADMIN 전용이다 — 기관 관리자는 여전히
 * {@code /institutions/{id}/enrollments} 를 써야 한다.
 */
@WebMvcTest(AdminEnrollmentController.class)
@ActiveProfiles("local")
@TestPropertySource(properties = "dabaeum.security.dev.bearer-token=admin-enrollment-token")
@Import({
    EnrollmentQueryApiMapper.class,
    ApiExceptionHandler.class,
    ClockConfiguration.class,
    SecurityConfiguration.class,
    DevBearerSecurityConfiguration.class,
    LocalDevSecuritySupportConfiguration.class,
    JacksonConfiguration.class,
    RequestIdFilter.class
})
class AdminEnrollmentControllerTest {

    private static final UUID USER_ID = UUID.fromString("40000000-0000-0000-0000-000000000004");
    private static final UUID COURSE_ID = UUID.fromString("90000000-0000-0000-0000-000000000009");
    private static final UUID INSTITUTION_ID = UUID.fromString("50000000-0000-0000-0000-000000000005");

    @Autowired MockMvc mockMvc;
    @MockitoBean EnrollmentQueryService service;

    @Test
    void 전_기관_수강신청을_한_목록으로_본다() throws Exception {
        stubAnyList(page(EnrollmentStatus.APPLIED));

        mockMvc.perform(get("/api/v1/admin/enrollments").with(user("admin").roles("PLATFORM_ADMIN")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].institutionName").exists())
            .andExpect(jsonPath("$.data[0].courseTitle").exists())
            .andExpect(jsonPath("$.data[0].progressPercent").value(45))
            .andExpect(jsonPath("$.data[0].lastStudiedAt").value("2026-09-18T00:00:00Z"));
    }

    @Test
    void 출결이_없으면_진도율은_0이고_최근_학습일은_null이다() throws Exception {
        Instant now = Instant.parse("2026-09-18T00:00:00Z");
        InstitutionEnrollmentView noAttendance = new InstitutionEnrollmentView(
            UUID.randomUUID(), COURSE_ID, USER_ID, EnrollmentStatus.APPLIED, now, now,
            "AI 기초", "홍길동", "다배움기관", 0, null, null, null, null);
        stubAnyList(new InstitutionEnrollmentPage(List.of(noAttendance), 0, 20, 1, 1));

        mockMvc.perform(get("/api/v1/admin/enrollments").with(user("admin").roles("PLATFORM_ADMIN")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].progressPercent").value(0))
            .andExpect(jsonPath("$.data[0].lastStudiedAt").doesNotExist());
    }

    @Test
    void 상태로_거른다() throws Exception {
        stubAnyList(page(EnrollmentStatus.APPLIED));

        mockMvc.perform(get("/api/v1/admin/enrollments").param("status", "APPLIED")
                .with(user("admin").roles("PLATFORM_ADMIN")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[*].status", everyItem(equalTo("APPLIED"))));

        verify(service).listAllEnrollments(
            eq(EnrollmentStatus.APPLIED), isNull(), isNull(), isNull(), isNull(),
            anyInt(), anyInt(), anyString());
    }

    @Test
    void 기관과_강의로도_거른다() throws Exception {
        stubAnyList(page(EnrollmentStatus.APPLIED));

        mockMvc.perform(get("/api/v1/admin/enrollments")
                .param("institutionId", INSTITUTION_ID.toString())
                .param("courseId", COURSE_ID.toString())
                .with(user("admin").roles("PLATFORM_ADMIN")))
            .andExpect(status().isOk());

        verify(service).listAllEnrollments(
            isNull(), eq(INSTITUTION_ID), eq(COURSE_ID), isNull(), isNull(),
            anyInt(), anyInt(), anyString());
    }

    @Test
    void 수업방식으로_거른다() throws Exception {
        stubAnyList(page(EnrollmentStatus.APPLIED));

        mockMvc.perform(get("/api/v1/admin/enrollments").param("educationType", "OFFLINE")
                .with(user("admin").roles("PLATFORM_ADMIN")))
            .andExpect(status().isOk());

        verify(service).listAllEnrollments(
            isNull(), isNull(), isNull(), eq(CourseEducationType.OFFLINE), isNull(),
            anyInt(), anyInt(), anyString());
    }

    @Test
    void 검색어로_거른다() throws Exception {
        stubAnyList(page(EnrollmentStatus.APPLIED));

        mockMvc.perform(get("/api/v1/admin/enrollments").param("q", "AI 기초")
                .with(user("admin").roles("PLATFORM_ADMIN")))
            .andExpect(status().isOk());

        verify(service).listAllEnrollments(
            isNull(), isNull(), isNull(), isNull(), eq("ai 기초"),
            anyInt(), anyInt(), anyString());
    }

    @Test
    void 검색어가_너무_길면_422다() throws Exception {
        mockMvc.perform(get("/api/v1/admin/enrollments").param("q", "가".repeat(101))
                .with(user("admin").roles("PLATFORM_ADMIN")))
            .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void 알_수_없는_상태값은_400이다() throws Exception {
        mockMvc.perform(get("/api/v1/admin/enrollments").param("status", "NOPE")
                .with(user("admin").roles("PLATFORM_ADMIN")))
            .andExpect(status().isBadRequest());
    }

    @Test
    void 기관_관리자는_전체_수강신청을_볼_수_없다() throws Exception {
        mockMvc.perform(get("/api/v1/admin/enrollments")
                .with(user("manager").roles("INSTITUTION_ADMIN")))
            .andExpect(status().isForbidden());
    }

    @Test
    void 학습자는_전체_수강신청을_볼_수_없다() throws Exception {
        mockMvc.perform(get("/api/v1/admin/enrollments")
                .with(user("learner").roles("LEARNER")))
            .andExpect(status().isForbidden());
    }

    private void stubAnyList(InstitutionEnrollmentPage result) {
        lenient().when(service.listAllEnrollments(
                any(), any(), any(), any(), any(), anyInt(), anyInt(), anyString()))
            .thenReturn(result);
    }

    private InstitutionEnrollmentPage page(EnrollmentStatus status) {
        return new InstitutionEnrollmentPage(List.of(enrollment(status)), 0, 20, 1, 1);
    }

    private InstitutionEnrollmentView enrollment(EnrollmentStatus status) {
        Instant now = Instant.parse("2026-09-18T00:00:00Z");
        return new InstitutionEnrollmentView(
            UUID.randomUUID(), COURSE_ID, USER_ID, status, now, now,
            "AI 기초", "홍길동", "다배움기관", 45, now, now, null, null);
    }
}
