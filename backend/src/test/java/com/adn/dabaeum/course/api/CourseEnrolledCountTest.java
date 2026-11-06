package com.adn.dabaeum.course.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
import com.adn.dabaeum.common.security.CurrentUserProvider;
import com.adn.dabaeum.common.web.RequestIdFilter;
import com.adn.dabaeum.course.application.CourseApplicationService;
import com.adn.dabaeum.course.application.CoursePage;
import com.adn.dabaeum.course.domain.Course;
import com.adn.dabaeum.course.domain.CourseEducationType;
import com.adn.dabaeum.course.domain.CourseStatus;
import com.adn.dabaeum.course.domain.CourseView;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
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
 * D-4 미리보기의 "3 / 16명", B-2 충족률, E-1 카드의 분자를 만드는 enrolledCount 배선을 검증한다.
 * 실제 SQL 이 APPROVED 만 세는지는 CourseMapperIntegrationTest 에서 통합 테스트로 증명한다.
 */
@WebMvcTest(CourseController.class)
@ActiveProfiles("local")
@TestPropertySource(properties =
    "dabaeum.security.dev.bearer-token=course-enrolled-count-test-token")
@Import({
    CourseApiMapper.class,
    ApiExceptionHandler.class,
    ClockConfiguration.class,
    SecurityConfiguration.class,
    DevBearerSecurityConfiguration.class,
    LocalDevSecuritySupportConfiguration.class,
    JacksonConfiguration.class,
    RequestIdFilter.class
})
class CourseEnrolledCountTest {

    private static final UUID COURSE_ID = UUID.fromString(
        "33333333-3333-3333-3333-333333333333"
    );

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    CourseApplicationService service;

    @MockitoBean
    CurrentUserProvider currentUserProvider;

    @Test
    void 승인된_신청_수가_상세_응답에_담긴다() throws Exception {
        when(service.get(eq(COURSE_ID), any())).thenReturn(new CourseView(course(), 2, null, null));

        mockMvc.perform(get("/api/v1/courses/{courseId}", COURSE_ID)
                .with(user("reader").roles("USER")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.enrolledCount").value(2));
    }

    @Test
    void 신청이_없으면_0이다() throws Exception {
        when(service.get(eq(COURSE_ID), any())).thenReturn(new CourseView(course(), 0, null, null));

        mockMvc.perform(get("/api/v1/courses/{courseId}", COURSE_ID)
                .with(user("reader").roles("USER")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.enrolledCount").value(0));
    }

    @Test
    void 목록에서도_강좌마다_집계가_온다() throws Exception {
        when(service.list(any(), any())).thenReturn(new CoursePage(
            List.of(new CourseView(course(), 3, null, null)), 0, 20, 1, 1));

        mockMvc.perform(get("/api/v1/courses")
                .with(user("reader").roles("USER")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].enrolledCount").value(3));
    }

    @Test
    void CourseView_는_course_없이_만들_수_없다() {
        assertThat(org.assertj.core.api.Assertions.catchThrowable(
            () -> new CourseView(null, 0, null, null)))
            .isInstanceOf(NullPointerException.class);
    }

    private Course course() {
        Instant instant = Instant.parse("2026-08-04T00:00:00Z");
        return new Course(
            COURSE_ID,
            UUID.fromString("22222222-2222-2222-2222-222222222222"),
            "COURSE-001",
            "과정 제목",
            "상세 설명",
            "TSIX_040201",
            CourseEducationType.HYBRID,
            LocalDate.of(2026, 9, 1),
            LocalDate.of(2026, 10, 31),
            LocalDate.of(2026, 8, 1),
            LocalDate.of(2026, 8, 25),
            30,
            "서울 교육장",
            "https://example.test/course",
            true,
            new BigDecimal("12.50"),
            CourseStatus.RECRUITING,
            instant,
            instant,
            null
        );
    }
}
