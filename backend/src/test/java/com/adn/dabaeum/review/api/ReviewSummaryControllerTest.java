package com.adn.dabaeum.review.api;

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
import com.adn.dabaeum.review.application.CourseReviewApplicationService;
import com.adn.dabaeum.review.application.CourseReviewPage;
import com.adn.dabaeum.review.domain.CourseReviewSummary;
import com.adn.dabaeum.review.domain.CourseReviewView;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

@WebMvcTest(CourseReviewController.class)
@ActiveProfiles("local")
@TestPropertySource(properties = "dabaeum.security.dev.bearer-token=review-summary-token")
@Import({
    ApiExceptionHandler.class,
    ClockConfiguration.class,
    SecurityConfiguration.class,
    DevBearerSecurityConfiguration.class,
    LocalDevSecuritySupportConfiguration.class,
    JacksonConfiguration.class,
    RequestIdFilter.class
})
class ReviewSummaryControllerTest {

    private static final UUID USER_ID = UUID.fromString(
        "40000000-0000-0000-0000-000000000004");
    private static final UUID COURSE_ID = UUID.fromString(
        "60000000-0000-0000-0000-000000000006");

    @Autowired MockMvc mockMvc;
    @MockitoBean CourseReviewApplicationService service;
    @MockitoBean CurrentUserProvider currentUserProvider;

    @Test
    void 강좌_수강평_요약이_평균과_분포를_준다() throws Exception {
        when(service.summarizeByCourse(eq(COURSE_ID))).thenReturn(
            new CourseReviewSummary(4L, new BigDecimal("3.75"), distribution(2L, 1L, 0L, 0L, 1L)));

        mockMvc.perform(get("/api/v1/courses/{courseId}/reviews/summary", COURSE_ID)
                .with(user("learner").roles("LEARNER")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.totalCount").value(4))
            .andExpect(jsonPath("$.data.averageRating").value(3.75))
            .andExpect(jsonPath("$.data.distribution.star5").value(2))
            .andExpect(jsonPath("$.data.distribution.star4").value(1))
            .andExpect(jsonPath("$.data.distribution.star3").value(0))
            .andExpect(jsonPath("$.data.distribution.star2").value(0))
            .andExpect(jsonPath("$.data.distribution.star1").value(1))
            .andExpect(jsonPath("$.data.writableCount").doesNotExist());
    }

    @Test
    void 수강평이_없으면_평균은_null_이고_분포는_전부_0이다() throws Exception {
        UUID emptyCourse = UUID.fromString("60000000-0000-0000-0000-000000000007");
        when(service.summarizeByCourse(eq(emptyCourse))).thenReturn(
            new CourseReviewSummary(0L, null, distribution(0L, 0L, 0L, 0L, 0L)));

        mockMvc.perform(get("/api/v1/courses/{courseId}/reviews/summary", emptyCourse)
                .with(user("learner").roles("LEARNER")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.totalCount").value(0))
            .andExpect(jsonPath("$.data.averageRating").doesNotExist())
            .andExpect(jsonPath("$.data.distribution.star5").value(0))
            .andExpect(jsonPath("$.data.distribution.star4").value(0))
            .andExpect(jsonPath("$.data.distribution.star3").value(0))
            .andExpect(jsonPath("$.data.distribution.star2").value(0))
            .andExpect(jsonPath("$.data.distribution.star1").value(0));
    }

    @Test
    void 목록의_작성자_이름은_마스킹돼_나간다() throws Exception {
        when(currentUserProvider.requireContext()).thenReturn(learner());
        when(service.listByCourse(eq(COURSE_ID), anyInt(), anyInt()))
            .thenReturn(new CourseReviewPage(List.of(review("시영자")), 0, 20, 1, 1));

        mockMvc.perform(get("/api/v1/courses/{courseId}/reviews", COURSE_ID)
                .with(user("learner").roles("LEARNER")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].userName").value("시*자"))
            .andExpect(jsonPath("$.data[0].userId").doesNotExist());
    }

    @Test
    void 나의_수강평_요약은_작성가능_건수를_준다() throws Exception {
        when(currentUserProvider.requireContext()).thenReturn(learner());
        when(service.summarizeMine(any())).thenReturn(
            new CourseReviewSummary(2L, new BigDecimal("5.00"), distribution(2L, 0L, 0L, 0L, 0L)));
        when(service.countWritableReviews(any())).thenReturn(2L);

        mockMvc.perform(get("/api/v1/users/me/reviews/summary")
                .with(user("learner").roles("LEARNER")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.writableCount").value(2))
            .andExpect(jsonPath("$.data.distribution.star5").value(2));
    }

    private static Map<String, Long> distribution(
        long count5, long count4, long count3, long count2, long count1
    ) {
        Map<String, Long> distribution = new LinkedHashMap<>();
        distribution.put("star5", count5);
        distribution.put("star4", count4);
        distribution.put("star3", count3);
        distribution.put("star2", count2);
        distribution.put("star1", count1);
        return distribution;
    }

    private CourseReviewView review(String userName) {
        Instant now = Instant.parse("2026-08-27T00:00:00Z");
        return new CourseReviewView(
            UUID.randomUUID(), USER_ID, COURSE_ID, 5, "좋아요", now,
            "AI 기초", userName, "다배움기관", "홍길동", "TSIX_010201", "ONLINE",
            10, null, null, null, null);
    }

    private AuthenticatedUserContext learner() {
        return new AuthenticatedUserContext(USER_ID, "LOCAL",
            Set.of(new AuthenticatedRole("LEARNER", null)));
    }
}
