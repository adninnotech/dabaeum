package com.adn.dabaeum.recommendation.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.adn.dabaeum.common.api.ApiErrorCode;
import com.adn.dabaeum.common.api.ApiException;
import com.adn.dabaeum.common.api.ApiExceptionHandler;
import com.adn.dabaeum.common.config.ClockConfiguration;
import com.adn.dabaeum.common.config.DevBearerSecurityConfiguration;
import com.adn.dabaeum.common.config.JacksonConfiguration;
import com.adn.dabaeum.common.config.LocalDevSecuritySupportConfiguration;
import com.adn.dabaeum.common.config.SecurityConfiguration;
import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import com.adn.dabaeum.common.security.AuthenticatedUserPrincipal;
import com.adn.dabaeum.common.web.RequestIdFilter;
import com.adn.dabaeum.course.api.CourseApiMapper;
import com.adn.dabaeum.course.domain.Course;
import com.adn.dabaeum.course.domain.CourseEducationType;
import com.adn.dabaeum.course.domain.CourseStatus;
import com.adn.dabaeum.course.domain.CourseView;
import com.adn.dabaeum.recommendation.application.CourseRecommendationService;
import com.adn.dabaeum.recommendation.application.RecommendationReason;
import com.adn.dabaeum.recommendation.application.RecommendationResult;
import com.adn.dabaeum.recommendation.application.RecommendedCourse;
import com.adn.dabaeum.recommendation.domain.RecommendationBasis;
import com.adn.dabaeum.recommendation.domain.RecommendationReasonType;
import com.adn.dabaeum.recommendation.domain.RecommendationTopFactor;
import com.adn.dabaeum.recommendation.domain.ReferenceSignal;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** docs/specs/course-recommendation.md §6, §12.5 — 비로그인 허용, exclude 해석, 응답 계약(모델 점수 포함), 클릭은 학습자만. */
@WebMvcTest(CourseRecommendationController.class)
@ActiveProfiles("local")
@TestPropertySource(properties = "dabaeum.security.dev.bearer-token=recommendation-token")
@Import({
    ApiExceptionHandler.class,
    ClockConfiguration.class,
    SecurityConfiguration.class,
    DevBearerSecurityConfiguration.class,
    LocalDevSecuritySupportConfiguration.class,
    JacksonConfiguration.class,
    RequestIdFilter.class,
    CourseApiMapper.class
})
class CourseRecommendationControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean CourseRecommendationService service;

    @Test
    void 비로그인도_조회하고_exclude는_쉼표_구분으로_받는다() throws Exception {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UUID third = UUID.randomUUID();
        UUID reference = UUID.randomUUID();
        ArgumentCaptor<List<UUID>> exclude = ArgumentCaptor.captor();
        when(service.recommend(eq(Optional.empty()), exclude.capture(), eq(4))).thenReturn(new RecommendationResult(
            RecommendationBasis.PERSONAL, List.of(
                new RecommendedCourse(null, view(), RecommendationBasis.PERSONAL, 3, new RecommendationReason(
                    RecommendationReasonType.SIMILAR_CLASSIFICATION, "TSIX_040102", "생활스포츠", ReferenceSignal.INTEREST,
                    reference, "배드민턴 입문", null, null, null)),
                new RecommendedCourse(null, view(), RecommendationBasis.POPULAR, 0,
                    new RecommendationReason(RecommendationReasonType.POPULAR, null, null, null, null, null, null, null, null)))));

        mockMvc.perform(get("/api/v1/courses/recommended")
                .param("limit", "4")
                .param("exclude", first + ", " + second + "," + third))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.basis").value("PERSONAL"))
            .andExpect(jsonPath("$.data.items[0].matchLevel").value(3))
            .andExpect(jsonPath("$.data.items[0].reason.classificationName").value("생활스포츠"))
            .andExpect(jsonPath("$.data.items[1].reason.type").value("POPULAR"));

        assertThat(exclude.getValue()).containsExactly(first, second, third);
    }

    @Test
    void 로그인하면_사용자_문맥을_넘기고_impressionId가_내려간다() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID impression = UUID.randomUUID();
        ArgumentCaptor<Optional<AuthenticatedUserContext>> viewer = ArgumentCaptor.captor();
        when(service.recommend(viewer.capture(), eq(List.of()), isNull())).thenReturn(new RecommendationResult(
            RecommendationBasis.AGE_BAND, List.of(new RecommendedCourse(impression, view(), RecommendationBasis.AGE_BAND, 0,
                new RecommendationReason(RecommendationReasonType.POPULAR_AGE_BAND, null, null, null, null, null, "30", null,
                    null)))));

        mockMvc.perform(get("/api/v1/courses/recommended").with(principal(userId, "LEARNER")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.basis").value("AGE_BAND"))
            .andExpect(jsonPath("$.data.items[0].impressionId").value(impression.toString()))
            .andExpect(jsonPath("$.data.items[0].reason.ageBand").value("30"));

        assertThat(viewer.getValue()).map(AuthenticatedUserContext::userId).contains(userId);
    }

    @Test
    void 모델_점수_항목은_basis_MODEL과_score_topFactor로_계약에_맞게_내려간다() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID impression = UUID.randomUUID();
        when(service.recommend(any(), eq(List.of()), isNull())).thenReturn(new RecommendationResult(
            RecommendationBasis.MODEL, List.of(
                new RecommendedCourse(impression, view(), RecommendationBasis.MODEL, 0, new RecommendationReason(
                    RecommendationReasonType.MODEL_SCORE, null, null, null, null, null, null,
                    new BigDecimal("0.81234"), RecommendationTopFactor.AGE_BAND)),
                new RecommendedCourse(UUID.randomUUID(), view(), RecommendationBasis.POPULAR, 0,
                    new RecommendationReason(RecommendationReasonType.POPULAR, null, null, null, null, null, null,
                        null, null)))));

        mockMvc.perform(get("/api/v1/courses/recommended").with(principal(userId, "LEARNER")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.basis").value("MODEL"))
            .andExpect(jsonPath("$.data.items[0].matchLevel").value(0))
            .andExpect(jsonPath("$.data.items[0].reason.type").value("MODEL_SCORE"))
            .andExpect(jsonPath("$.data.items[0].reason.score").value(0.81234))
            .andExpect(jsonPath("$.data.items[0].reason.topFactor").value("AGE_BAND"))
            .andExpect(jsonPath("$.data.items[1].reason.score").isEmpty());
    }

    @Test
    void exclude가_UUID가_아니면_400() throws Exception {
        mockMvc.perform(get("/api/v1/courses/recommended").param("exclude", "not-a-uuid"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
            .andExpect(jsonPath("$.details[0]").value("exclude"));

        verifyNoInteractions(service);
    }

    @Test
    void 클릭은_학습자만_기록하고_없으면_404() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID impression = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/courses/recommended/{id}/click", impression))
            .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/courses/recommended/{id}/click", impression)
                .with(principal(userId, "INSTITUTION_ADMIN")))
            .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/courses/recommended/{id}/click", impression).with(principal(userId, "LEARNER")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.accepted").value(true));
        ArgumentCaptor<AuthenticatedUserContext> context = ArgumentCaptor.forClass(AuthenticatedUserContext.class);
        verify(service).click(eq(impression), context.capture());
        assertThat(context.getValue().userId()).isEqualTo(userId);

        org.mockito.Mockito.doThrow(new ApiException(HttpStatus.NOT_FOUND,
                ApiErrorCode.RECOMMENDATION_IMPRESSION_NOT_FOUND, "Recommendation impression not found"))
            .when(service).click(eq(impression), any());
        mockMvc.perform(post("/api/v1/courses/recommended/{id}/click", impression).with(principal(userId, "LEARNER")))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("RECOMMENDATION_IMPRESSION_NOT_FOUND"));
    }

    private static RequestPostProcessor principal(UUID userId, String role) {
        return authentication(new UsernamePasswordAuthenticationToken(
            new AuthenticatedUserPrincipal(userId, Set.of("ROLE_" + role)),
            "N/A",
            Set.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }

    private static CourseView view() {
        Instant now = Instant.parse("2026-10-14T03:00:00Z");
        Course course = new Course(UUID.randomUUID(), UUID.randomUUID(), "C-" + UUID.randomUUID(), "탁구 입문",
            "설명", "TSIX_040102", CourseEducationType.OFFLINE, LocalDate.of(2026, 10, 20), LocalDate.of(2026, 11, 26),
            LocalDate.of(2026, 10, 13), LocalDate.of(2026, 10, 19), 20, "대구시민체육관", null, false, null,
            CourseStatus.RECRUITING, now, now, null);
        return new CourseView(course, 3, "기관", "강사");
    }
}
