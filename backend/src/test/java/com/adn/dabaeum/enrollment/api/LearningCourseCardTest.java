package com.adn.dabaeum.enrollment.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
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
import com.adn.dabaeum.course.domain.CourseFeeType;
import com.adn.dabaeum.enrollment.application.EnrollmentQueryService;
import com.adn.dabaeum.enrollment.application.LearningCoursePage;
import com.adn.dabaeum.common.text.NameMask;
import com.adn.dabaeum.enrollment.domain.LearningCourseView;
import com.adn.dabaeum.inquiry.api.InquiryApiMapper;
import com.adn.dabaeum.inquiry.api.InquiryController;
import com.adn.dabaeum.inquiry.application.InquiryApplicationService;
import com.adn.dabaeum.inquiry.application.InquiryViewPage;
import com.adn.dabaeum.inquiry.domain.InquiryStatus;
import com.adn.dabaeum.inquiry.domain.InquiryView;
import com.adn.dabaeum.interest.api.CourseInterestController;
import com.adn.dabaeum.interest.application.CourseInterestApplicationService;
import com.adn.dabaeum.interest.application.CourseInterestPage;
import com.adn.dabaeum.interest.domain.CourseInterestView;
import com.adn.dabaeum.review.api.CourseReviewController;
import com.adn.dabaeum.review.application.CourseReviewApplicationService;
import com.adn.dabaeum.review.application.CourseReviewPage;
import com.adn.dabaeum.review.domain.CourseReviewView;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 학습 목록 4탭(수강 중·관심·문의·후기)이 같은 카드 필드(강사명·카테고리·회차수·교육비)를
 * 주는지 확인한다. 서비스는 mock 이라 SQL 조인 자체는 검증하지 않는다 — 그건
 * enrollment.infrastructure 통합 테스트가 한다.
 */
@WebMvcTest({
    EnrollmentQueryController.class,
    CourseInterestController.class,
    InquiryController.class,
    CourseReviewController.class
})
@ActiveProfiles("local")
@TestPropertySource(properties = "dabaeum.security.dev.bearer-token=learning-card-token")
@Import({
    EnrollmentQueryApiMapper.class,
    InquiryApiMapper.class,
    ApiExceptionHandler.class,
    ClockConfiguration.class,
    SecurityConfiguration.class,
    DevBearerSecurityConfiguration.class,
    LocalDevSecuritySupportConfiguration.class,
    JacksonConfiguration.class,
    RequestIdFilter.class
})
class LearningCourseCardTest {

    private static final UUID USER_ID = UUID.fromString(
        "40000000-0000-0000-0000-000000000004");

    @Autowired MockMvc mockMvc;
    @MockitoBean EnrollmentQueryService enrollmentQueryService;
    @MockitoBean CourseInterestApplicationService courseInterestApplicationService;
    @MockitoBean InquiryApplicationService inquiryApplicationService;
    @MockitoBean CourseReviewApplicationService courseReviewApplicationService;
    @MockitoBean CurrentUserProvider currentUserProvider;

    @Test
    void 나의_강의_카드가_강사명_카테고리_회차수를_준다() throws Exception {
        when(currentUserProvider.requireContext()).thenReturn(learner());
        when(enrollmentQueryService.listLearningCourses(any(), any(), any(), anyInt(), anyInt()))
            .thenReturn(new LearningCoursePage(
                List.of(learningCourse("강사입니다", 5), noInstructor(), noSessions()),
                0, 20, 3, 1));

        mockMvc.perform(get("/api/v1/users/me/learning-courses")
                .with(user("learner").roles("LEARNER")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].institutionName").value("서울평생교육원"))
            .andExpect(jsonPath("$.data[0].instructorName").value("강사입니다"))
            .andExpect(jsonPath("$.data[0].classificationCode").value("TSIX_040102"))
            .andExpect(jsonPath("$.data[0].sessionCount").value(5))
            .andExpect(jsonPath("$.data[0].tuitionType").value("PAID"))
            .andExpect(jsonPath("$.data[0].tuitionAmount").value(40000));
    }

    @Test
    void 강사가_없는_강좌는_instructorName_이_null_이다() throws Exception {
        when(currentUserProvider.requireContext()).thenReturn(learner());
        when(enrollmentQueryService.listLearningCourses(any(), any(), any(), anyInt(), anyInt()))
            .thenReturn(new LearningCoursePage(
                List.of(learningCourse("강사입니다", 5), noInstructor(), noSessions()),
                0, 20, 3, 1));

        mockMvc.perform(get("/api/v1/users/me/learning-courses")
                .with(user("learner").roles("LEARNER")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[1].instructorName").doesNotExist());
    }

    @Test
    void 회차가_없는_강좌는_sessionCount_가_0이다() throws Exception {
        when(currentUserProvider.requireContext()).thenReturn(learner());
        when(enrollmentQueryService.listLearningCourses(any(), any(), any(), anyInt(), anyInt()))
            .thenReturn(new LearningCoursePage(
                List.of(learningCourse("강사입니다", 5), noInstructor(), noSessions()),
                0, 20, 3, 1));

        mockMvc.perform(get("/api/v1/users/me/learning-courses")
                .with(user("learner").roles("LEARNER")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[2].sessionCount").value(0));
    }

    @Test
    void 네_목록이_같은_카드필드를_준다() throws Exception {
        when(currentUserProvider.requireContext()).thenReturn(learner());
        when(enrollmentQueryService.listLearningCourses(any(), any(), any(), anyInt(), anyInt()))
            .thenReturn(new LearningCoursePage(
                List.of(learningCourse("강사입니다", 5)), 0, 20, 1, 1));
        when(courseInterestApplicationService.listMine(any(), any(), anyInt(), anyInt()))
            .thenReturn(new CourseInterestPage(List.of(interest()), 0, 20, 1, 1));
        when(inquiryApplicationService.listMine(any(), any(), any(), anyInt(), anyInt()))
            .thenReturn(new InquiryViewPage(List.of(inquiry()), 0, 20, 1, 1));
        when(courseReviewApplicationService.listMine(any(), any(), anyInt(), anyInt()))
            .thenReturn(new CourseReviewPage(List.of(review()), 0, 20, 1, 1));

        for (String path : List.of(
                "/api/v1/users/me/learning-courses",
                "/api/v1/users/me/interests",
                "/api/v1/users/me/inquiries",
                "/api/v1/users/me/reviews")) {
            mockMvc.perform(get(path).with(user("learner").roles("LEARNER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].institutionName").exists())
                .andExpect(jsonPath("$.data[0].classificationCode").exists())
                .andExpect(jsonPath("$.data[0].educationType").exists());
        }
    }

    @Test
    void 내_후기_목록은_내_이름을_가리지_않고_강좌_후기_목록은_가린다() throws Exception {
        when(currentUserProvider.requireContext()).thenReturn(learner());
        when(courseReviewApplicationService.listMine(any(), any(), anyInt(), anyInt()))
            .thenReturn(new CourseReviewPage(List.of(review()), 0, 20, 1, 1));
        when(courseReviewApplicationService.listByCourse(any(), anyInt(), anyInt()))
            .thenReturn(new CourseReviewPage(List.of(review()), 0, 20, 1, 1));

        mockMvc.perform(get("/api/v1/users/me/reviews").with(user("learner").roles("LEARNER")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].userName").value("학습자"));
        mockMvc.perform(get("/api/v1/courses/{courseId}/reviews", UUID.randomUUID())
                .with(user("learner").roles("LEARNER")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].userName").value(NameMask.mask("학습자")));
    }

    private LearningCourseView learningCourse(String instructorName, int sessionCount) {
        Instant now = Instant.parse("2026-08-27T00:00:00Z");
        LocalDate day = LocalDate.parse("2026-09-01");
        return new LearningCourseView(
            UUID.randomUUID(), UUID.randomUUID(), "건강 교실", "서울평생교육원",
            "IN_PROGRESS", "OFFLINE", day, day, now, instructorName, "TSIX_040102",
            sessionCount, "실내,체조", CourseFeeType.PAID, BigDecimal.valueOf(40000),
            UUID.randomUUID(), 0);
    }

    private LearningCourseView noInstructor() {
        return learningCourse(null, 5);
    }

    private LearningCourseView noSessions() {
        return learningCourse("강사입니다", 0);
    }

    private CourseInterestView interest() {
        Instant now = Instant.parse("2026-08-27T00:00:00Z");
        return new CourseInterestView(
            UUID.randomUUID(), UUID.randomUUID(), now, "건강 교실", "서울평생교육원",
            "TSIX_040102", "OFFLINE", "RECRUITING", "강사입니다", 5, "실내,체조",
            CourseFeeType.PAID, BigDecimal.valueOf(40000), UUID.randomUUID());
    }

    private InquiryView inquiry() {
        Instant now = Instant.parse("2026-08-27T00:00:00Z");
        return new InquiryView(
            UUID.randomUUID(), USER_ID, UUID.randomUUID(), "문의 제목",
            InquiryStatus.PENDING, com.adn.dabaeum.inquiry.domain.InquiryType.COURSE, now,
            "건강 교실",
            new com.adn.dabaeum.enrollment.domain.EnrolleeProfile(
                "학습자", null, null, null, null, null),
            "서울평생교육원", "강사입니다", "TSIX_040102", "OFFLINE", 5, "실내,체조", CourseFeeType.PAID,
            BigDecimal.valueOf(40000), UUID.randomUUID());
    }

    private CourseReviewView review() {
        Instant now = Instant.parse("2026-08-27T00:00:00Z");
        return new CourseReviewView(
            UUID.randomUUID(), USER_ID, UUID.randomUUID(), 5, "좋아요", now,
            "건강 교실", "학습자", "서울평생교육원", "강사입니다", "TSIX_040102", "OFFLINE",
            5, "실내,체조", CourseFeeType.PAID, BigDecimal.valueOf(40000),
            UUID.randomUUID());
    }

    private AuthenticatedUserContext learner() {
        return new AuthenticatedUserContext(USER_ID, "LOCAL",
            Set.of(new AuthenticatedRole("LEARNER", null)));
    }
}
