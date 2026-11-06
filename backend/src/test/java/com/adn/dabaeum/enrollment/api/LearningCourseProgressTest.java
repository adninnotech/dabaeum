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
import com.adn.dabaeum.enrollment.domain.LearningCourseView;
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
 * 나의 강의 카드가 행마다 진도율을 따로 부르지 않고 목록 응답에 출석수·진도율을 함께 준다.
 * 실제 조인 SQL 은 {@code LearningCourseProgressIntegrationTest} 가 검증한다 — 여기는
 * 응답 직렬화와 0 나누기 방지만 본다.
 */
@WebMvcTest(EnrollmentQueryController.class)
@ActiveProfiles("local")
@TestPropertySource(properties = "dabaeum.security.dev.bearer-token=learning-progress-token")
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
class LearningCourseProgressTest {

    private static final UUID USER_ID = UUID.fromString(
        "40000000-0000-0000-0000-000000000004");

    @Autowired MockMvc mockMvc;
    @MockitoBean EnrollmentQueryService enrollmentQueryService;
    @MockitoBean CurrentUserProvider currentUserProvider;

    @Test
    void 카드가_출석수와_진도율을_같이_준다() throws Exception {
        when(currentUserProvider.requireContext()).thenReturn(learner());
        when(enrollmentQueryService.listLearningCourses(any(), any(), any(), anyInt(), anyInt()))
            .thenReturn(new LearningCoursePage(
                List.of(learningCourse(2, 5), learningCourse(0, 0)), 0, 20, 2, 1));

        mockMvc.perform(get("/api/v1/users/me/learning-courses")
                .with(user("learner").roles("LEARNER")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].attendedCount").value(2))
            .andExpect(jsonPath("$.data[0].sessionCount").value(5))
            .andExpect(jsonPath("$.data[0].progressPercent").value(40));
    }

    @Test
    void 회차가_없으면_진도율은_0이고_나눗셈이_터지지_않는다() throws Exception {
        when(currentUserProvider.requireContext()).thenReturn(learner());
        when(enrollmentQueryService.listLearningCourses(any(), any(), any(), anyInt(), anyInt()))
            .thenReturn(new LearningCoursePage(
                List.of(learningCourse(2, 5), learningCourse(0, 0)), 0, 20, 2, 1));

        mockMvc.perform(get("/api/v1/users/me/learning-courses")
                .with(user("learner").roles("LEARNER")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[1].sessionCount").value(0))
            .andExpect(jsonPath("$.data[1].attendedCount").value(0))
            .andExpect(jsonPath("$.data[1].progressPercent").value(0));
    }

    private LearningCourseView learningCourse(int attendedCount, int sessionCount) {
        Instant now = Instant.parse("2026-08-27T00:00:00Z");
        LocalDate day = LocalDate.parse("2026-09-01");
        return new LearningCourseView(
            UUID.randomUUID(), UUID.randomUUID(), "건강 교실", "서울평생교육원",
            "IN_PROGRESS", "OFFLINE", day, day, now, "강사입니다", "TSIX_040102",
            sessionCount, "실내,체조", CourseFeeType.PAID, BigDecimal.valueOf(40000),
            UUID.randomUUID(), attendedCount);
    }

    private AuthenticatedUserContext learner() {
        return new AuthenticatedUserContext(USER_ID, "LOCAL",
            Set.of(new AuthenticatedRole("LEARNER", null)));
    }
}
