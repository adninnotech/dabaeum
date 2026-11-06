package com.adn.dabaeum.enrollment.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.adn.dabaeum.common.api.ApiErrorCode;
import com.adn.dabaeum.common.api.ApiException;
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
import com.adn.dabaeum.course.api.CourseQueryApiMapper;
import com.adn.dabaeum.course.api.CourseQueryController;
import com.adn.dabaeum.course.application.CourseQueryService;
import com.adn.dabaeum.course.application.InstructorCoursePage;
import com.adn.dabaeum.course.domain.Course;
import com.adn.dabaeum.course.domain.CourseEducationType;
import com.adn.dabaeum.course.domain.CourseFeeType;
import com.adn.dabaeum.course.domain.CourseInstructorRole;
import com.adn.dabaeum.course.domain.CourseStatus;
import com.adn.dabaeum.course.domain.InstructorCourseStats;
import com.adn.dabaeum.course.domain.InstructorCourseView;
import com.adn.dabaeum.enrollment.application.EnrollmentQueryService;
import com.adn.dabaeum.enrollment.application.LearningCoursePage;
import com.adn.dabaeum.enrollment.application.MyEnrollmentPage;
import com.adn.dabaeum.enrollment.domain.EnrolleeProfile;
import com.adn.dabaeum.enrollment.domain.EnrollmentStatus;
import com.adn.dabaeum.enrollment.domain.LearningCourseView;
import com.adn.dabaeum.enrollment.domain.MyEnrollmentView;
import com.adn.dabaeum.enrollment.domain.UserLearningStats;
import com.adn.dabaeum.inquiry.api.InquiryApiMapper;
import com.adn.dabaeum.inquiry.api.InquiryController;
import com.adn.dabaeum.inquiry.application.InquiryApplicationService;
import com.adn.dabaeum.inquiry.application.InquiryViewPage;
import com.adn.dabaeum.inquiry.domain.InquiryStatus;
import com.adn.dabaeum.inquiry.domain.InquiryType;
import com.adn.dabaeum.inquiry.domain.InquiryView;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 스펙 #40 · B-4 회원 상세 — 관리자 전용 학습 이력 조회.
 *
 * <p>가장 위험한 부분은 인가 규칙표다. {@code /users/*&#47;enrollments} 같은 와일드카드 규칙이
 * {@code /users/me/enrollments} 를 삼키면 학습자가 자기 이력에서 403 을 받는다(이 프로젝트에서
 * {@code /users/*&#47;identities} 로 실제로 겪었다). 그래서 이 테스트는 관리자 경로와 함께
 * <b>me 경로가 여전히 열려 있는지</b>도 같이 고정한다 — 그러려면 me 컨트롤러도 함께 띄워야 한다.
 */
@WebMvcTest({
    AdminUserLearningController.class,
    EnrollmentQueryController.class,
    InquiryController.class,
    CourseQueryController.class
})
@ActiveProfiles("local")
@TestPropertySource(properties = "dabaeum.security.dev.bearer-token=admin-user-learning-token")
@Import({
    EnrollmentQueryApiMapper.class,
    InquiryApiMapper.class,
    CourseQueryApiMapper.class,
    CourseApiMapper.class,
    ApiExceptionHandler.class,
    ClockConfiguration.class,
    SecurityConfiguration.class,
    DevBearerSecurityConfiguration.class,
    LocalDevSecuritySupportConfiguration.class,
    JacksonConfiguration.class,
    RequestIdFilter.class
})
class AdminUserLearningTest {

    private static final UUID TARGET_USER = UUID.fromString(
        "40000000-0000-0000-0000-000000000004");
    private static final UUID UNKNOWN_USER = UUID.fromString(
        "00000000-0000-0000-0000-0000000000ff");
    private static final UUID INSTRUCTOR_USER = UUID.fromString(
        "60000000-0000-0000-0000-000000000006");
    private static final UUID INSTITUTION_ID = UUID.fromString(
        "50000000-0000-0000-0000-000000000005");

    @Autowired MockMvc mockMvc;
    @MockitoBean EnrollmentQueryService enrollmentQueryService;
    @MockitoBean InquiryApplicationService inquiryService;
    @MockitoBean CourseQueryService courseQueryService;
    @MockitoBean CurrentUserProvider currentUserProvider;

    @Test
    void 관리자가_남의_수강_목록을_본다() throws Exception {
        when(enrollmentQueryService.listEnrollmentsOf(
                eq(TARGET_USER), any(), anyInt(), anyInt(), anyString()))
            .thenReturn(new MyEnrollmentPage(List.of(myEnrollment()), 0, 20, 1, 1));

        mockMvc.perform(get("/api/v1/users/{userId}/enrollments", TARGET_USER)
                .with(user("admin").roles("PLATFORM_ADMIN")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].courseTitle").value("AI 기초"))
            .andExpect(jsonPath("$.page.totalElements").value(1));
    }

    @Test
    void 관리자가_남의_강의_목록을_본다() throws Exception {
        when(enrollmentQueryService.listLearningCoursesOf(
                eq(TARGET_USER), any(), any(), anyInt(), anyInt()))
            .thenReturn(new LearningCoursePage(List.of(learningCourse()), 0, 20, 1, 1));

        mockMvc.perform(get("/api/v1/users/{userId}/learning-courses", TARGET_USER)
                .with(user("admin").roles("PLATFORM_ADMIN")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].title").value("건강 교실"));
    }

    @Test
    void 관리자가_남의_문의_목록을_본다() throws Exception {
        when(inquiryService.listOf(eq(TARGET_USER), any(), any(), anyInt(), anyInt()))
            .thenReturn(new InquiryViewPage(List.of(inquiry()), 0, 20, 1, 1));

        mockMvc.perform(get("/api/v1/users/{userId}/inquiries", TARGET_USER)
                .with(user("admin").roles("PLATFORM_ADMIN")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].title").value("문의 제목"));
    }

    @Test
    void 관리자가_남의_학습_통계를_본다() throws Exception {
        when(enrollmentQueryService.learningStatsOf(TARGET_USER))
            .thenReturn(new UserLearningStats(7, 2, 4, 540, 62, 3));

        mockMvc.perform(get("/api/v1/users/{userId}/learning-stats", TARGET_USER)
                .with(user("admin").roles("PLATFORM_ADMIN")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.totalCount").value(7))
            .andExpect(jsonPath("$.data.inProgressCount").value(2))
            .andExpect(jsonPath("$.data.completedCount").value(4))
            .andExpect(jsonPath("$.data.totalStudyMinutes").value(540))
            .andExpect(jsonPath("$.data.averageProgressPercent").value(62))
            .andExpect(jsonPath("$.data.inquiryCount").value(3));
    }

    @Test
    void 관리자가_남의_강사_배정_과정을_본다() throws Exception {
        when(courseQueryService.listInstructorCoursesOf(
                eq(INSTRUCTOR_USER), any(), anyInt(), anyInt(), anyString()))
            .thenReturn(new InstructorCoursePage(
                List.of(new InstructorCourseView(course(), CourseInstructorRole.MAIN, 7, null, null)),
                0, 20, 1, 1));

        mockMvc.perform(get("/api/v1/instructors/{userId}/courses", INSTRUCTOR_USER)
                .with(user("admin").roles("PLATFORM_ADMIN")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].instructorRole").value("MAIN"));
    }

    @Test
    void 강사_통계도_남의_것을_본다() throws Exception {
        when(courseQueryService.instructorCourseStatsOf(INSTRUCTOR_USER))
            .thenReturn(new InstructorCourseStats(4, 1, 2, 1, 7, 3));

        mockMvc.perform(get("/api/v1/instructors/{userId}/courses/stats", INSTRUCTOR_USER)
                .with(user("admin").roles("PLATFORM_ADMIN")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.total").value(4))
            .andExpect(jsonPath("$.data.studentCount").value(7))
            .andExpect(jsonPath("$.data.inquiryCount").value(3));
    }

    @Test
    void 학습자는_남의_이력을_볼_수_없다() throws Exception {
        for (String path : List.of(
            "/api/v1/users/" + TARGET_USER + "/enrollments",
            "/api/v1/users/" + TARGET_USER + "/learning-courses",
            "/api/v1/users/" + TARGET_USER + "/inquiries",
            "/api/v1/users/" + TARGET_USER + "/learning-stats",
            "/api/v1/instructors/" + INSTRUCTOR_USER + "/courses",
            "/api/v1/instructors/" + INSTRUCTOR_USER + "/courses/stats"
        )) {
            mockMvc.perform(get(path).with(user("learner").roles("LEARNER")))
                .andExpect(status().isForbidden());
        }
    }

    /** 와일드카드 규칙이 me 를 삼키면 여기서 걸린다. 이 Task 의 회귀 방지선이다. */
    @Test
    void me_경로가_여전히_학습자에게_열려_있다() throws Exception {
        when(currentUserProvider.requireContext()).thenReturn(learner());
        when(enrollmentQueryService.listMyEnrollments(
                any(), any(), anyInt(), anyInt(), anyString()))
            .thenReturn(new MyEnrollmentPage(List.of(myEnrollment()), 0, 20, 1, 1));
        when(enrollmentQueryService.listLearningCourses(
                any(), any(), any(), anyInt(), anyInt()))
            .thenReturn(new LearningCoursePage(List.of(learningCourse()), 0, 20, 1, 1));
        when(inquiryService.listMine(any(), any(), any(), anyInt(), anyInt()))
            .thenReturn(new InquiryViewPage(List.of(inquiry()), 0, 20, 1, 1));

        mockMvc.perform(get("/api/v1/users/me/enrollments")
                .with(user("learner").roles("LEARNER")))
            .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/users/me/learning-courses")
                .with(user("learner").roles("LEARNER")))
            .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/users/me/inquiries")
                .with(user("learner").roles("LEARNER")))
            .andExpect(status().isOk());
    }

    /** 강사 me 경로도 /instructors/*&#47;courses 규칙에 가리면 안 된다. */
    @Test
    void 강사_me_경로가_여전히_열려_있다() throws Exception {
        when(currentUserProvider.requireContext()).thenReturn(instructor());
        when(courseQueryService.listInstructorCourses(
                any(), any(), anyInt(), anyInt(), anyString()))
            .thenReturn(new InstructorCoursePage(
                List.of(new InstructorCourseView(course(), CourseInstructorRole.MAIN, 7, null, null)),
                0, 20, 1, 1));
        when(courseQueryService.instructorCourseStats(any()))
            .thenReturn(new InstructorCourseStats(4, 1, 2, 1, 7, 3));

        mockMvc.perform(get("/api/v1/instructors/me/courses")
                .with(user("instructor").roles("INSTRUCTOR")))
            .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/instructors/me/courses/stats")
                .with(user("instructor").roles("INSTRUCTOR")))
            .andExpect(status().isOk());
    }

    @Test
    void 없는_사용자는_빈_페이지가_아니라_404_다() throws Exception {
        when(enrollmentQueryService.listEnrollmentsOf(
                eq(UNKNOWN_USER), any(), anyInt(), anyInt(), anyString()))
            .thenThrow(new ApiException(
                HttpStatus.NOT_FOUND, ApiErrorCode.USER_NOT_FOUND, "User not found"));

        mockMvc.perform(get("/api/v1/users/{userId}/enrollments", UNKNOWN_USER)
                .with(user("admin").roles("PLATFORM_ADMIN")))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }

    private MyEnrollmentView myEnrollment() {
        Instant now = Instant.parse("2026-08-27T00:00:00Z");
        return new MyEnrollmentView(
            UUID.randomUUID(), UUID.randomUUID(), TARGET_USER,
            EnrollmentStatus.APPROVED, now, now,
            "AI 기초", "AI-101", CourseStatus.IN_PROGRESS, "다배움기관",
            null, null, null, null, null, null, null, null, null);
    }

    private LearningCourseView learningCourse() {
        Instant now = Instant.parse("2026-08-27T00:00:00Z");
        LocalDate day = LocalDate.parse("2026-09-01");
        return new LearningCourseView(
            UUID.randomUUID(), UUID.randomUUID(), "건강 교실", "서울평생교육원",
            "IN_PROGRESS", "OFFLINE", day, day, now, "강사입니다", "TSIX_040102",
            5, "실내,체조", CourseFeeType.PAID, BigDecimal.valueOf(40000),
            UUID.randomUUID(), 2);
    }

    private InquiryView inquiry() {
        Instant now = Instant.parse("2026-09-16T00:00:00Z");
        EnrolleeProfile profile = new EnrolleeProfile(
            "시드학습자", null, null, "seed.learner@dabaeum.kr", "010-1234-5678", null);
        return new InquiryView(
            UUID.randomUUID(), TARGET_USER, UUID.randomUUID(), "문의 제목",
            InquiryStatus.PENDING, InquiryType.COURSE, now, "AI 기초", profile,
            "다배움기관", "강사", "TSIX_010201", "OFFLINE", 5, "태그", null, null, null);
    }

    private Course course() {
        Instant now = Instant.parse("2026-08-27T00:00:00Z");
        return new Course(
            UUID.randomUUID(), INSTITUTION_ID, "AI-101", "AI 기초",
            null, null, CourseEducationType.OFFLINE,
            LocalDate.parse("2026-09-01"), LocalDate.parse("2026-12-01"),
            null, null, 30, null, null, false, null,
            CourseStatus.IN_PROGRESS, now, now, null);
    }

    private AuthenticatedUserContext learner() {
        return new AuthenticatedUserContext(TARGET_USER, "LOCAL",
            Set.of(new AuthenticatedRole("LEARNER", null)));
    }

    private AuthenticatedUserContext instructor() {
        return new AuthenticatedUserContext(INSTRUCTOR_USER, "LOCAL",
            Set.of(new AuthenticatedRole("INSTRUCTOR", INSTITUTION_ID)));
    }
}
