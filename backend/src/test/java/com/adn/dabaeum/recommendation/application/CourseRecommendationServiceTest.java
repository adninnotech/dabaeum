package com.adn.dabaeum.recommendation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.adn.dabaeum.common.api.ApiErrorCode;
import com.adn.dabaeum.common.api.ApiException;
import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import com.adn.dabaeum.course.domain.Course;
import com.adn.dabaeum.course.domain.CourseRepository;
import com.adn.dabaeum.course.domain.CourseView;
import com.adn.dabaeum.popularity.application.PopularityProperties;
import com.adn.dabaeum.popularity.domain.PopularitySegment;
import com.adn.dabaeum.recommendation.domain.CandidateFilter;
import com.adn.dabaeum.recommendation.domain.InterestCourse;
import com.adn.dabaeum.recommendation.domain.RecommendationBasis;
import com.adn.dabaeum.recommendation.domain.RecommendationImpression;
import com.adn.dabaeum.recommendation.domain.RecommendationReasonType;
import com.adn.dabaeum.recommendation.domain.RecommendationRepository;
import com.adn.dabaeum.recommendation.domain.RecommendationTopFactor;
import com.adn.dabaeum.recommendation.domain.ReferenceCourse;
import com.adn.dabaeum.recommendation.domain.ReferenceSignal;
import com.adn.dabaeum.recommendation.domain.ScoredCourse;
import com.adn.dabaeum.recommendation.domain.SimilarCourse;
import com.adn.dabaeum.user.domain.User;
import com.adn.dabaeum.user.domain.UserInterestRepository;
import com.adn.dabaeum.user.domain.UserRepository;
import com.adn.dabaeum.user.domain.UserStatus;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

/** docs/specs/course-recommendation.md §3~§7, §12.5 — 모델 점수, 순위 선택, 폴백 순서, 제외 전달, 노출 로그, 입력 검증. */
@ExtendWith(MockitoExtension.class)
class CourseRecommendationServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-14T03:00:00Z"); // 서울 12:00
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 14);
    private static final UUID INSTITUTION = UUID.randomUUID();

    @Mock RecommendationRepository recommendations;
    @Mock CourseRepository courses;
    @Mock UserRepository users;
    @Mock UserInterestRepository interests;
    private CourseRecommendationService service;

    @BeforeEach
    void setUp() {
        service = new CourseRecommendationService(recommendations, courses, users, interests,
            new RecommendationProperties(null, null, null, null, null), new PopularityProperties(null, null, null),
            Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void 비로그인은_전체_인기만_타고_로그를_남기지_않는다() {
        UUID exclude = UUID.randomUUID();
        UUID popular = UUID.randomUUID();
        ArgumentCaptor<CandidateFilter> filter = ArgumentCaptor.forClass(CandidateFilter.class);
        when(recommendations.findPopular(eq(PopularitySegment.ALL), eq("*"), filter.capture(), eq(30), eq(6)))
            .thenReturn(List.of(popular));
        CourseView popularView = view(popular);
        when(courses.findActiveViewsByIds(List.of(popular))).thenReturn(List.of(popularView));

        RecommendationResult result = service.recommend(Optional.empty(), List.of(exclude), null);

        assertThat(result.basis()).isEqualTo(RecommendationBasis.POPULAR);
        assertThat(result.items()).singleElement().satisfies(item -> {
            assertThat(item.impressionId()).isNull();
            assertThat(item.matchLevel()).isZero();
            assertThat(item.reason().type()).isEqualTo(RecommendationReasonType.POPULAR);
        });
        assertThat(filter.getValue().userId()).isNull();
        assertThat(filter.getValue().excludeCourseIds()).containsExactly(exclude);
        assertThat(filter.getValue().today()).isEqualTo(TODAY);
        assertThat(filter.getValue().adultViewer()).isFalse();
        verify(recommendations, never()).findReferenceCourses(any());
        verify(recommendations, never()).saveImpressions(anyList());
    }

    @Test
    void 기준강좌가_있으면_PERSONAL이고_모자라면_관심분야_연령대_전체_순으로_채우며_고른_것은_다시_뺀다() {
        UUID userId = UUID.randomUUID();
        UUID reference = UUID.randomUUID();
        UUID similar = UUID.randomUUID();
        UUID byAge = UUID.randomUUID();
        UUID byInterest = UUID.randomUUID();
        when(users.findById(userId)).thenReturn(Optional.of(user(userId, LocalDate.of(1990, 5, 1))));
        when(recommendations.findReferenceCourses(userId)).thenReturn(List.of(
            new ReferenceCourse(reference, "배드민턴 입문", "TSIX_040102", ReferenceSignal.INTEREST, NOW)));
        when(recommendations.findSimilar(eq(userId), any(), eq(30), eq(NOW.minusSeconds(7 * 86_400)), eq(5), eq(3)))
            .thenReturn(List.of(new SimilarCourse(similar, 3, "TSIX_040102", "생활스포츠", reference, "배드민턴 입문",
                ReferenceSignal.INTEREST)));
        when(interests.findCodes(userId)).thenReturn(List.of("TSIX_040000"));
        ArgumentCaptor<CandidateFilter> interestFilter = ArgumentCaptor.forClass(CandidateFilter.class);
        when(recommendations.findInterestCourses(eq(List.of("TSIX_040000")), interestFilter.capture(), eq(30), eq(2)))
            .thenReturn(List.of(new InterestCourse(byInterest, "TSIX_040000", "문화예술교육")));
        ArgumentCaptor<CandidateFilter> ageFilter = ArgumentCaptor.forClass(CandidateFilter.class);
        when(recommendations.findPopular(eq(PopularitySegment.AGE_BAND), eq("30"), ageFilter.capture(), eq(30), eq(1)))
            .thenReturn(List.of(byAge));
        List<CourseView> loaded = List.of(view(byAge), view(similar), view(byInterest));
        when(courses.findActiveViewsByIds(List.of(similar, byInterest, byAge))).thenReturn(loaded);

        RecommendationResult result = service.recommend(Optional.of(context(userId)), List.of(), 3);

        assertThat(result.basis()).isEqualTo(RecommendationBasis.PERSONAL);
        assertThat(result.items()).extracting(item -> item.course().course().id())
            .containsExactly(similar, byInterest, byAge);
        assertThat(result.items()).extracting(RecommendedCourse::basis)
            .containsExactly(RecommendationBasis.PERSONAL, RecommendationBasis.INTEREST, RecommendationBasis.AGE_BAND);
        assertThat(result.items().get(0).reason().sourceCourseTitle()).isEqualTo("배드민턴 입문");
        assertThat(result.items().get(1).reason().type()).isEqualTo(RecommendationReasonType.INTEREST);
        assertThat(result.items().get(1).reason().classificationCode()).isEqualTo("TSIX_040000");
        assertThat(result.items().get(1).reason().classificationName()).isEqualTo("문화예술교육");
        assertThat(result.items().get(2).reason().ageBand()).isEqualTo("30");
        assertThat(interestFilter.getValue().excludeCourseIds()).containsExactly(similar);
        assertThat(ageFilter.getValue().excludeCourseIds()).containsExactly(similar, byInterest);
        assertThat(ageFilter.getValue().adultViewer()).isTrue();
        verify(recommendations, never()).findPopular(eq(PopularitySegment.ALL), any(), any(), anyInt(), anyInt());

        ArgumentCaptor<List<RecommendationImpression>> logged = ArgumentCaptor.captor();
        verify(recommendations).saveImpressions(logged.capture());
        assertThat(logged.getValue()).extracting(RecommendationImpression::position).containsExactly(1, 2, 3);
        assertThat(logged.getValue().get(0).reasonType()).isEqualTo(RecommendationReasonType.SIMILAR_CLASSIFICATION);
        assertThat(logged.getValue().get(0).matchLevel()).isEqualTo(3);
        assertThat(result.items()).extracting(RecommendedCourse::impressionId)
            .containsExactlyElementsOf(logged.getValue().stream().map(RecommendationImpression::id).toList());
    }

    @Test
    void 기준강좌도_관심분야도_생년월일도_없으면_전체_인기로_간다() {
        UUID userId = UUID.randomUUID();
        when(users.findById(userId)).thenReturn(Optional.of(user(userId, null)));
        when(recommendations.findReferenceCourses(userId)).thenReturn(List.of());
        when(recommendations.findPopular(eq(PopularitySegment.ALL), eq("*"), any(), eq(30), eq(6)))
            .thenReturn(List.of());

        RecommendationResult result = service.recommend(Optional.of(context(userId)), List.of(), null);

        assertThat(result.basis()).isEqualTo(RecommendationBasis.POPULAR);
        assertThat(result.items()).isEmpty();
        verify(recommendations, never()).findSimilar(any(), any(), anyInt(), any(), anyInt(), anyInt());
        verify(recommendations, never()).findPopular(eq(PopularitySegment.AGE_BAND), any(), any(), anyInt(), anyInt());
        verify(recommendations, never()).findInterestCourses(any(), any(), anyInt(), anyInt());
        verify(recommendations, never()).saveImpressions(anyList());
    }

    @Test
    void 비로그인은_관심분야를_조회하지_않는다() {
        when(recommendations.findPopular(eq(PopularitySegment.ALL), eq("*"), any(), eq(30), eq(6)))
            .thenReturn(List.of());

        service.recommend(Optional.empty(), List.of(), null);

        Mockito.verifyNoInteractions(interests);
    }

    @Test
    void 학습자가_아닌_토큰은_비로그인처럼_전체_인기만_타고_로그를_남기지_않는다() {
        UUID adminId = UUID.randomUUID();
        AuthenticatedUserContext admin = new AuthenticatedUserContext(adminId, "LOCAL",
            Set.of(new com.adn.dabaeum.common.security.AuthenticatedRole("INSTITUTION_ADMIN", UUID.randomUUID())));
        ArgumentCaptor<CandidateFilter> filter = ArgumentCaptor.forClass(CandidateFilter.class);
        when(recommendations.findPopular(eq(PopularitySegment.ALL), eq("*"), filter.capture(), eq(30), eq(6)))
            .thenReturn(List.of());

        RecommendationResult result = service.recommend(Optional.of(admin), List.of(), null);

        assertThat(result.basis()).isEqualTo(RecommendationBasis.POPULAR);
        assertThat(filter.getValue().userId()).isNull();
        verify(users, never()).findById(any());
        verify(recommendations, never()).findReferenceCourses(any());
        verify(recommendations, never()).saveImpressions(anyList());
    }

    @Test
    void 집계_사이에_비공개가_된_강좌는_응답에서_빠진다() {
        UUID gone = UUID.randomUUID();
        when(recommendations.findPopular(eq(PopularitySegment.ALL), eq("*"), any(), eq(30), eq(6)))
            .thenReturn(List.of(gone));
        when(courses.findActiveViewsByIds(List.of(gone))).thenReturn(List.of());

        assertThat(service.recommend(Optional.empty(), List.of(), null).items()).isEmpty();
    }

    @Test
    void limit_범위_밖이거나_exclude가_많으면_400() {
        assertBadRequest(() -> service.recommend(Optional.empty(), List.of(), 0), "limit");
        assertBadRequest(() -> service.recommend(Optional.empty(), List.of(), 21), "limit");
        List<UUID> tooMany = java.util.stream.Stream.generate(UUID::randomUUID).limit(21).toList();
        assertBadRequest(() -> service.recommend(Optional.empty(), tooMany, null), "exclude");
        Mockito.verifyNoInteractions(recommendations);
    }

    @Test
    void 점수_행이_있으면_MODEL_로_먼저_채우고_모자라면_PERSONAL_폴백으로_채우며_같은_강좌는_다시_뽑지_않는다() {
        UUID userId = UUID.randomUUID();
        UUID exclude = UUID.randomUUID();
        UUID scored = UUID.randomUUID();
        UUID reference = UUID.randomUUID();
        UUID similar = UUID.randomUUID();
        UUID popular = UUID.randomUUID();
        when(users.findById(userId)).thenReturn(Optional.of(user(userId, null)));
        ArgumentCaptor<CandidateFilter> modelFilter = ArgumentCaptor.forClass(CandidateFilter.class);
        when(recommendations.findModelScored(eq(userId), modelFilter.capture(), eq(3))).thenReturn(List.of(
            new ScoredCourse(scored, new BigDecimal("0.81234"), RecommendationTopFactor.CLASSIFICATION)));
        when(recommendations.findReferenceCourses(userId)).thenReturn(List.of(
            new ReferenceCourse(reference, "배드민턴 입문", "TSIX_040102", ReferenceSignal.INTEREST, NOW)));
        ArgumentCaptor<CandidateFilter> personalFilter = ArgumentCaptor.forClass(CandidateFilter.class);
        when(recommendations.findSimilar(eq(userId), personalFilter.capture(), eq(30), any(), eq(5), eq(2)))
            .thenReturn(List.of(new SimilarCourse(similar, 2, "TSIX_040100", "스포츠", reference, "배드민턴 입문",
                ReferenceSignal.INTEREST)));
        ArgumentCaptor<CandidateFilter> popularFilter = ArgumentCaptor.forClass(CandidateFilter.class);
        when(recommendations.findPopular(eq(PopularitySegment.ALL), eq("*"), popularFilter.capture(), eq(30), eq(1)))
            .thenReturn(List.of(popular));
        List<CourseView> loaded = List.of(view(popular), view(scored), view(similar));
        when(courses.findActiveViewsByIds(List.of(scored, similar, popular))).thenReturn(loaded);

        RecommendationResult result = service.recommend(Optional.of(context(userId)), List.of(exclude), 3);

        assertThat(result.basis()).isEqualTo(RecommendationBasis.MODEL);
        assertThat(result.items()).extracting(item -> item.course().course().id())
            .containsExactly(scored, similar, popular);
        assertThat(result.items()).extracting(RecommendedCourse::basis)
            .containsExactly(RecommendationBasis.MODEL, RecommendationBasis.PERSONAL, RecommendationBasis.POPULAR);
        assertThat(result.items().get(0)).satisfies(item -> {
            assertThat(item.matchLevel()).isZero();
            assertThat(item.reason().type()).isEqualTo(RecommendationReasonType.MODEL_SCORE);
            assertThat(item.reason().score()).isEqualByComparingTo("0.81234");
            assertThat(item.reason().topFactor()).isEqualTo(RecommendationTopFactor.CLASSIFICATION);
        });
        assertThat(modelFilter.getValue().excludeCourseIds()).containsExactly(exclude);
        assertThat(personalFilter.getValue().excludeCourseIds()).containsExactly(exclude, scored);
        assertThat(popularFilter.getValue().excludeCourseIds()).containsExactly(exclude, scored, similar);

        ArgumentCaptor<List<RecommendationImpression>> logged = ArgumentCaptor.captor();
        verify(recommendations).saveImpressions(logged.capture());
        assertThat(logged.getValue().get(0).basis()).isEqualTo(RecommendationBasis.MODEL);
        assertThat(logged.getValue().get(0).reasonType()).isEqualTo(RecommendationReasonType.MODEL_SCORE);
        assertThat(logged.getValue().get(0).matchLevel()).isZero();
    }

    @Test
    void 점수_행으로_limit을_채우면_규칙_경로를_부르지_않는다() {
        UUID userId = UUID.randomUUID();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        when(users.findById(userId)).thenReturn(Optional.of(user(userId, LocalDate.of(1990, 5, 1))));
        when(recommendations.findModelScored(eq(userId), any(), eq(2))).thenReturn(List.of(
            new ScoredCourse(first, new BigDecimal("0.9"), RecommendationTopFactor.INTEREST),
            new ScoredCourse(second, new BigDecimal("0.4"), RecommendationTopFactor.POPULARITY)));
        List<CourseView> loaded = List.of(view(first), view(second));
        when(courses.findActiveViewsByIds(List.of(first, second))).thenReturn(loaded);

        RecommendationResult result = service.recommend(Optional.of(context(userId)), List.of(), 2);

        assertThat(result.basis()).isEqualTo(RecommendationBasis.MODEL);
        assertThat(result.items()).extracting(item -> item.course().course().id()).containsExactly(first, second);
        verify(recommendations, never()).findReferenceCourses(any());
        verify(recommendations, never()).findPopular(any(), any(), any(), anyInt(), anyInt());
    }

    @Test
    void 점수_행이_없으면_기존과_같다() {
        UUID userId = UUID.randomUUID();
        UUID popular = UUID.randomUUID();
        when(users.findById(userId)).thenReturn(Optional.of(user(userId, null)));
        when(recommendations.findModelScored(eq(userId), any(), eq(6))).thenReturn(List.of());
        when(recommendations.findReferenceCourses(userId)).thenReturn(List.of());
        ArgumentCaptor<CandidateFilter> filter = ArgumentCaptor.forClass(CandidateFilter.class);
        when(recommendations.findPopular(eq(PopularitySegment.ALL), eq("*"), filter.capture(), eq(30), eq(6)))
            .thenReturn(List.of(popular));
        List<CourseView> loaded = List.of(view(popular));
        when(courses.findActiveViewsByIds(List.of(popular))).thenReturn(loaded);

        RecommendationResult result = service.recommend(Optional.of(context(userId)), List.of(), null);

        assertThat(result.basis()).isEqualTo(RecommendationBasis.POPULAR);
        assertThat(result.items()).singleElement().satisfies(item -> {
            assertThat(item.reason().type()).isEqualTo(RecommendationReasonType.POPULAR);
            assertThat(item.reason().score()).isNull();
            assertThat(item.reason().topFactor()).isNull();
        });
        assertThat(filter.getValue().excludeCourseIds()).isEmpty();
    }

    @Test
    void 비로그인은_모델_점수를_조회하지_않는다() {
        when(recommendations.findPopular(eq(PopularitySegment.ALL), eq("*"), any(), eq(30), eq(6)))
            .thenReturn(List.of());

        service.recommend(Optional.empty(), List.of(), null);

        verify(recommendations, never()).findModelScored(any(), any(), anyInt());
    }

    @Test
    void 클릭은_본인_노출만_기록하고_없으면_404() {
        UUID userId = UUID.randomUUID();
        UUID impression = UUID.randomUUID();
        when(recommendations.markClicked(impression, userId, NOW)).thenReturn(true, false);

        service.click(impression, context(userId));
        assertThatThrownBy(() -> service.click(impression, context(userId)))
            .isInstanceOfSatisfying(ApiException.class, exception ->
                assertThat(exception.code()).isEqualTo(ApiErrorCode.RECOMMENDATION_IMPRESSION_NOT_FOUND));
    }

    @Test
    void 연령대는_10세_단위이고_10대이하_70대이상으로_묶는다() {
        assertThat(CourseRecommendationService.ageBand(7)).isEqualTo("10");
        assertThat(CourseRecommendationService.ageBand(19)).isEqualTo("10");
        assertThat(CourseRecommendationService.ageBand(36)).isEqualTo("30");
        assertThat(CourseRecommendationService.ageBand(84)).isEqualTo("70");
    }

    @Test
    void 모델_항목과_같은_기관_같은_제목인_규칙_항목은_순위_경계에서도_뺀다() {
        UUID userId = UUID.randomUUID();
        UUID scored = UUID.randomUUID();
        UUID reference = UUID.randomUUID();
        UUID sameTitle = UUID.randomUUID();
        UUID popular = UUID.randomUUID();
        when(users.findById(userId)).thenReturn(Optional.of(user(userId, null)));
        when(recommendations.findModelScored(eq(userId), any(), eq(3))).thenReturn(List.of(
            new ScoredCourse(scored, new BigDecimal("0.70000"), RecommendationTopFactor.SCHEDULE)));
        when(recommendations.findReferenceCourses(userId)).thenReturn(List.of(
            new ReferenceCourse(reference, "배드민턴 입문", "TSIX_040102", ReferenceSignal.INTEREST, NOW)));
        when(recommendations.findSimilar(eq(userId), any(), eq(30), any(), eq(5), eq(2)))
            .thenReturn(List.of(new SimilarCourse(sameTitle, 2, "TSIX_040100", "스포츠", reference, "배드민턴 입문",
                ReferenceSignal.INTEREST)));
        when(recommendations.findPopular(eq(PopularitySegment.ALL), eq("*"), any(), eq(30), eq(1)))
            .thenReturn(List.of(popular));
        List<CourseView> loaded = List.of(
            view(scored, INSTITUTION, "요가 기초 "), view(sameTitle, INSTITUTION, "요가 기초"), view(popular));
        when(courses.findActiveViewsByIds(List.of(scored, sameTitle, popular))).thenReturn(loaded);

        RecommendationResult result = service.recommend(Optional.of(context(userId)), List.of(), 3);

        assertThat(result.items()).extracting(item -> item.course().course().id()).containsExactly(scored, popular);
        assertThat(result.items()).extracting(RecommendedCourse::basis)
            .containsExactly(RecommendationBasis.MODEL, RecommendationBasis.POPULAR);
    }

    private static AuthenticatedUserContext context(UUID userId) {
        return new AuthenticatedUserContext(userId, "LOCAL",
            Set.of(new com.adn.dabaeum.common.security.AuthenticatedRole("LEARNER", null)));
    }

    private static User user(UUID id, LocalDate birthDate) {
        return new User(id, "학생", "student@example.test", null, birthDate, UserStatus.ACTIVE, null, NOW, NOW,
            null, null, null, null, null, true, false, null);
    }

    private static CourseView view(UUID id) {
        return view(id, INSTITUTION, "강좌 " + id);
    }

    private static CourseView view(UUID id, UUID institutionId, String title) {
        Course course = Mockito.mock(Course.class);
        Mockito.lenient().when(course.id()).thenReturn(id);
        Mockito.lenient().when(course.institutionId()).thenReturn(institutionId);
        Mockito.lenient().when(course.title()).thenReturn(title);
        return new CourseView(course, 0, "기관", "강사");
    }

    private static void assertBadRequest(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, String field) {
        assertThatThrownBy(call).isInstanceOfSatisfying(ApiException.class, exception -> {
            assertThat(exception.code()).isEqualTo(ApiErrorCode.BAD_REQUEST);
            assertThat(exception.details()).containsExactly(field);
        });
    }
}
