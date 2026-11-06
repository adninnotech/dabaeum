package com.adn.dabaeum.enrollment.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
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
import com.adn.dabaeum.enrollment.application.EnrollmentProgressPage;
import com.adn.dabaeum.enrollment.application.EnrollmentQueryService;
import com.adn.dabaeum.enrollment.domain.EnrolleeProfile;
import com.adn.dabaeum.enrollment.domain.EnrollmentProgressView;
import java.time.Instant;
import java.time.LocalDate;
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

/** C-4 수강 현황: 수강생 정보·최근 학습일. */
@WebMvcTest(EnrollmentQueryController.class)
@ActiveProfiles("local")
@TestPropertySource(properties = "dabaeum.security.dev.bearer-token=enrollment-progress-token")
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
class EnrollmentProgressDetailTest {

    private static final UUID USER_ID = UUID.fromString("40000000-0000-0000-0000-000000000004");
    private static final UUID ENROLLMENT_ID = UUID.fromString("60000000-0000-0000-0000-000000000006");
    private static final UUID NO_ATTENDANCE_ID = UUID.fromString("70000000-0000-0000-0000-000000000007");
    private static final UUID COURSE_ID = UUID.fromString("80000000-0000-0000-0000-000000000008");

    @Autowired MockMvc mockMvc;
    @MockitoBean EnrollmentQueryService service;
    @MockitoBean CurrentUserProvider currentUserProvider;

    @Test
    void 수강생_연락처와_생년월일이_온다() throws Exception {
        when(currentUserProvider.requireContext()).thenReturn(manager());
        when(service.listInstructorEnrollmentProgress(any(), any(), anyInt(), anyInt()))
            .thenReturn(new EnrollmentProgressPage(
                List.of(progress(ENROLLMENT_ID, Instant.parse("2026-09-16T01:47:00Z"))),
                0, 20, 1, 1));

        mockMvc.perform(get("/api/v1/instructors/me/enrollment-status")
                .with(user("manager").roles("INSTITUTION_ADMIN"))
                .param("courseId", COURSE_ID.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].userName").value("시드학습자"))
            .andExpect(jsonPath("$.data[0].phone").value("010-1234-5678"))
            .andExpect(jsonPath("$.data[0].birthDate").value("1990-03-15"))
            // 학습 진도(3/5)와 별개로, 학습자 출결 요약과 같은 규칙의 출석률을 준다(QA DATA-04)
            .andExpect(jsonPath("$.data[0].progressPercent").value(60))
            .andExpect(jsonPath("$.data[0].attendanceRate").value(75.00));
    }

    @Test
    void 관리자가_강사의_수강_현황을_userId_경로로_본다() throws Exception {
        when(service.listInstructorEnrollmentProgressOf(eq(USER_ID), any(), anyInt(), anyInt()))
            .thenReturn(new EnrollmentProgressPage(
                List.of(progress(ENROLLMENT_ID, Instant.parse("2026-09-16T01:47:00Z"))),
                0, 20, 1, 1));

        mockMvc.perform(get("/api/v1/instructors/{userId}/enrollment-status", USER_ID)
                .param("courseId", COURSE_ID.toString())
                .with(user("admin").roles("PLATFORM_ADMIN")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].userName").value("시드학습자"));
    }

    @Test
    void 없는_강사_userId는_404다() throws Exception {
        when(service.listInstructorEnrollmentProgressOf(any(), any(), anyInt(), anyInt()))
            .thenThrow(new com.adn.dabaeum.common.api.ApiException(
                org.springframework.http.HttpStatus.NOT_FOUND,
                com.adn.dabaeum.common.api.ApiErrorCode.USER_NOT_FOUND, "User not found"));

        mockMvc.perform(get("/api/v1/instructors/{userId}/enrollment-status", USER_ID)
                .with(user("admin").roles("PLATFORM_ADMIN")))
            .andExpect(status().isNotFound());
    }

    @Test
    void 학습자는_남의_강사_수강_현황을_볼_수_없다() throws Exception {
        mockMvc.perform(get("/api/v1/instructors/{userId}/enrollment-status", USER_ID)
                .with(user("learner").roles("LEARNER")))
            .andExpect(status().isForbidden());
    }

    @Test
    void 강사도_남의_강사_수강_현황을_볼_수_없다() throws Exception {
        mockMvc.perform(get("/api/v1/instructors/{userId}/enrollment-status", USER_ID)
                .with(user("manager").roles("INSTRUCTOR")))
            .andExpect(status().isForbidden());
    }

    @Test
    void 최근_학습일은_마지막_출석_시각이다() throws Exception {
        when(currentUserProvider.requireContext()).thenReturn(learner());
        when(service.enrollmentProgress(any(), eq(ENROLLMENT_ID)))
            .thenReturn(progress(ENROLLMENT_ID, Instant.parse("2026-09-16T01:47:00Z")));

        mockMvc.perform(get("/api/v1/enrollments/{id}/progress", ENROLLMENT_ID)
                .with(user("learner").roles("LEARNER")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.lastStudiedAt").value("2026-09-16T01:47:00Z"));
    }

    @Test
    void 출석이_없으면_최근_학습일이_없다() throws Exception {
        when(currentUserProvider.requireContext()).thenReturn(learner());
        when(service.enrollmentProgress(any(), eq(NO_ATTENDANCE_ID)))
            .thenReturn(progress(NO_ATTENDANCE_ID, null));

        mockMvc.perform(get("/api/v1/enrollments/{id}/progress", NO_ATTENDANCE_ID)
                .with(user("learner").roles("LEARNER")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.lastStudiedAt").doesNotExist());
    }

    private EnrollmentProgressView progress(UUID enrollmentId, Instant lastStudiedAt) {
        EnrolleeProfile profile = new EnrolleeProfile(
            "시드학습자", LocalDate.of(1990, 3, 15), null,
            "seed.learner@dabaeum.kr", "010-1234-5678", null);
        return new EnrollmentProgressView(enrollmentId, USER_ID, profile, COURSE_ID,
            "AI 기초", 3, 5, lastStudiedAt, "APPROVED", new java.math.BigDecimal("75.00"));
    }

    private AuthenticatedUserContext learner() {
        return new AuthenticatedUserContext(USER_ID, "LOCAL",
            Set.of(new AuthenticatedRole("LEARNER", null)));
    }

    private AuthenticatedUserContext manager() {
        return new AuthenticatedUserContext(UUID.randomUUID(), "LOCAL",
            Set.of(new AuthenticatedRole("INSTITUTION_ADMIN", UUID.randomUUID())));
    }
}
