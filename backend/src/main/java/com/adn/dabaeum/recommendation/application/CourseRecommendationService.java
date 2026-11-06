package com.adn.dabaeum.recommendation.application;

import com.adn.dabaeum.common.api.ApiErrorCode;
import com.adn.dabaeum.common.api.ApiException;
import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import com.adn.dabaeum.course.domain.CourseRepository;
import com.adn.dabaeum.course.domain.CourseView;
import com.adn.dabaeum.popularity.application.PopularityProperties;
import com.adn.dabaeum.popularity.domain.PopularitySegment;
import com.adn.dabaeum.recommendation.domain.CandidateFilter;
import com.adn.dabaeum.recommendation.domain.RecommendationBasis;
import com.adn.dabaeum.recommendation.domain.RecommendationImpression;
import com.adn.dabaeum.recommendation.domain.RecommendationRepository;
import com.adn.dabaeum.recommendation.domain.ReferenceCourse;
import com.adn.dabaeum.user.domain.User;
import com.adn.dabaeum.user.domain.UserInterestRepository;
import com.adn.dabaeum.user.domain.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 홈 맞춤 강좌 (docs/specs/course-recommendation.md). 요청 시 SQL 로 계산한다(§5).
 *
 * <p>순서(§12.5, §3, §4): 학습자의 모델 점수 행(MODEL) → 모자라면 찜·신청 기준 강좌의 분류 유사도(PERSONAL)
 * → 연령대 인기 → 지역 인기 → 전체 인기. 점수 행이 없으면 규칙 기반 순서만 탄다.
 * 어느 순위든 §3.4 제외 조건과 프론트가 넘긴 exclude(홈 인기 섹션)를 같이 적용한다.
 * 로그인 사용자의 응답은 노출 로그(§7)로 남긴다.
 */
@Service
@Profile({"local", "dev"})
public class CourseRecommendationService {

    static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    /** 성인 판정 경계(만 나이). 어린이·아동청소년 전용 강좌를 뺄 때만 쓴다. */
    static final int ADULT_AGE = 19;
    static final String LEARNER_ROLE = "LEARNER";

    private final RecommendationRepository recommendations;
    private final CourseRepository courses;
    private final UserRepository users;
    private final UserInterestRepository interests;
    private final RecommendationProperties properties;
    private final PopularityProperties popularity;
    private final Clock clock;

    public CourseRecommendationService(
        RecommendationRepository recommendations,
        CourseRepository courses,
        UserRepository users,
        UserInterestRepository interests,
        RecommendationProperties properties,
        PopularityProperties popularity,
        Clock clock
    ) {
        this.recommendations = Objects.requireNonNull(recommendations);
        this.courses = Objects.requireNonNull(courses);
        this.users = Objects.requireNonNull(users);
        this.interests = Objects.requireNonNull(interests);
        this.properties = Objects.requireNonNull(properties);
        this.popularity = Objects.requireNonNull(popularity);
        this.clock = Objects.requireNonNull(clock);
    }

    /** 로그인 사용자 응답은 노출 로그를 남기므로 읽기 전용이 아니다. */
    @Transactional
    public RecommendationResult recommend(
        Optional<AuthenticatedUserContext> viewer, List<UUID> excludeCourseIds, Integer requestedLimit
    ) {
        int limit = validatedLimit(requestedLimit);
        List<UUID> exclude = Objects.requireNonNullElse(excludeCourseIds, List.of());
        if (exclude.size() > properties.maxExclude()) {
            throw badRequest("exclude");
        }
        Instant now = clock.instant();
        LocalDate today = now.atZone(SEOUL).toLocalDate();

        // 학습자만 개인화한다(§6). 강사·관리자 토큰은 비로그인과 같이 본다 — 클릭도 학습자만 남길 수 있다.
        Optional<User> user = viewer.filter(CourseRecommendationService::isLearner)
            .map(AuthenticatedUserContext::userId)
            .flatMap(users::findById);
        UUID userId = user.map(User::id).orElse(null);
        Optional<Integer> age = user.flatMap(u -> Optional.ofNullable(u.birthDate()))
            .map(birth -> Period.between(birth, today).getYears());
        CandidateFilter filter = new CandidateFilter(
            userId,
            exclude,
            today,
            age.map(years -> years >= ADULT_AGE).orElse(false)
        );

        List<Pick> picks = new ArrayList<>();
        if (userId != null) {
            picks.addAll(modelScored(userId, filter, limit));
            if (picks.size() < limit) {
                CandidateFilter narrowed = filter.excludingAlso(picks.stream().map(Pick::courseId).toList());
                picks.addAll(personal(userId, narrowed, now, limit - picks.size()));
            }
            if (picks.size() < limit) {
                CandidateFilter narrowed = filter.excludingAlso(picks.stream().map(Pick::courseId).toList());
                picks.addAll(interestCourses(userId, narrowed, limit - picks.size()));
            }
        }
        for (Fallback fallback : fallbacks(age)) {
            if (picks.size() >= limit) {
                break;
            }
            CandidateFilter narrowed = filter.excludingAlso(picks.stream().map(Pick::courseId).toList());
            recommendations.findPopular(
                fallback.segment(),
                fallback.key(),
                narrowed,
                popularity.windowDays(), limit - picks.size()
            ).forEach(courseId -> picks.add(new Pick(courseId, fallback.basis(), 0, fallback.reason())));
        }

        List<RecommendedCourse> items = attachCourses(picks);
        if (userId != null && !items.isEmpty()) {
            items = logImpressions(userId, items, now);
        }
        RecommendationBasis basis = items.isEmpty() ? RecommendationBasis.POPULAR : items.getFirst().basis();
        return new RecommendationResult(basis, items);
    }

    @Transactional
    public void click(UUID impressionId, AuthenticatedUserContext viewer) {
        if (impressionId == null) {
            throw badRequest("impressionId");
        }
        if (!recommendations.markClicked(impressionId, viewer.userId(), clock.instant())) {
            throw new ApiException(HttpStatus.NOT_FOUND, ApiErrorCode.RECOMMENDATION_IMPRESSION_NOT_FOUND,
                "Recommendation impression not found");
        }
    }

    /** §12.5 야간 배치 점수. 일치 수준이 없으므로 matchLevel 0. */
    private List<Pick> modelScored(UUID userId, CandidateFilter filter, int limit) {
        return recommendations.findModelScored(userId, filter, limit).stream()
            .map(scored -> new Pick(scored.courseId(), RecommendationBasis.MODEL, 0,
                RecommendationReason.modelScore(scored)))
            .toList();
    }

    private List<Pick> personal(UUID userId, CandidateFilter filter, Instant now, int limit) {
        List<ReferenceCourse> references = recommendations.findReferenceCourses(userId);
        if (references.isEmpty()) {
            return List.of();
        }
        Instant freshSince = now.minus(Duration.ofDays(properties.freshDays()));
        return recommendations.findSimilar(userId, filter, popularity.windowDays(), freshSince,
                properties.freshBonus(), limit).stream()
            .map(similar -> new Pick(similar.courseId(), RecommendationBasis.PERSONAL, similar.matchLevel(),
                RecommendationReason.similar(similar)))
            .toList();
    }

    /** 회원이 고른 관심 분야. 고르지 않았으면 조회하지 않는다. */
    private List<Pick> interestCourses(UUID userId, CandidateFilter filter, int limit) {
        List<String> codes = interests.findCodes(userId);
        if (codes.isEmpty()) {
            return List.of();
        }
        return recommendations.findInterestCourses(codes, filter, popularity.windowDays(), limit).stream()
            .map(course -> new Pick(course.courseId(), RecommendationBasis.INTEREST, 0,
                RecommendationReason.interest(course)))
            .toList();
    }

    /** §4 순서. 비로그인·정보 없음이면 전체 인기만. */
    private static List<Fallback> fallbacks(Optional<Integer> age) {
        List<Fallback> chain = new ArrayList<>();
        age.map(CourseRecommendationService::ageBand).ifPresent(band -> chain.add(new Fallback(
            PopularitySegment.AGE_BAND, band, RecommendationBasis.AGE_BAND, RecommendationReason.popularAgeBand(band))));
        chain.add(new Fallback(PopularitySegment.ALL, "*", RecommendationBasis.POPULAR, RecommendationReason.popular()));
        return List.copyOf(chain);
    }

    static boolean isLearner(AuthenticatedUserContext context) {
        return context.roles().stream().anyMatch(role -> LEARNER_ROLE.equals(role.role()));
    }

    /** 만 나이 10세 단위, 10(10대 이하)…70(70대 이상). 인기 집계(PopularityMapper.ageBand)와 같은 규칙. */
    static String ageBand(int years) {
        int band = Math.clamp((years / 10) * 10, 10, 70);
        return Integer.toString(band);
    }

    private List<RecommendedCourse> attachCourses(List<Pick> picks) {
        if (picks.isEmpty()) {
            return List.of();
        }
        Map<UUID, CourseView> views = courses.findActiveViewsByIds(picks.stream().map(Pick::courseId).toList())
            .stream()
            .collect(Collectors.toMap(view -> view.course().id(), Function.identity()));
        Set<UUID> seen = new HashSet<>();
        Set<String> seenTitles = new HashSet<>();
        List<RecommendedCourse> items = new ArrayList<>();
        for (Pick pick : picks) {
            CourseView view = views.get(pick.courseId());
            // 각 순위(MODEL·PERSONAL·폴백)는 SQL 안에서만 같은 기관·같은 제목을 정리하므로 순위 경계는 여기서 한 번 더 걸러낸다(§3.4).
            if (view != null && seen.add(pick.courseId()) && seenTitles.add(titleKey(view))) {
                items.add(new RecommendedCourse(null, view, pick.basis(), pick.matchLevel(), pick.reason()));
            }
        }
        return List.copyOf(items);
    }

    private List<RecommendedCourse> logImpressions(UUID userId, List<RecommendedCourse> items, Instant now) {
        Map<RecommendedCourse, UUID> ids = new LinkedHashMap<>();
        List<RecommendationImpression> rows = new ArrayList<>();
        int position = 1;
        for (RecommendedCourse item : items) {
            UUID id = UUID.randomUUID();
            ids.put(item, id);
            rows.add(new RecommendationImpression(id, userId, item.course().course().id(), item.basis(),
                item.reason().type(), item.matchLevel(), position++, now));
        }
        recommendations.saveImpressions(rows);
        return ids.entrySet().stream().map(entry -> entry.getKey().withImpressionId(entry.getValue())).toList();
    }

    private int validatedLimit(Integer requested) {
        int limit = requested == null ? properties.defaultLimit() : requested;
        if (limit < 1 || limit > properties.maxLimit()) {
            throw badRequest("limit");
        }
        return limit;
    }

    private static ApiException badRequest(String field) {
        return new ApiException(HttpStatus.BAD_REQUEST, ApiErrorCode.BAD_REQUEST,
            "Invalid recommendation query", List.of(field));
    }

    private static String titleKey(CourseView view) {
        String title = view.course().title() == null ? "" : view.course().title().strip();
        return view.course().institutionId() + "\u0000" + title;
    }

    private record Pick(UUID courseId, RecommendationBasis basis, int matchLevel, RecommendationReason reason) {
    }

    private record Fallback(
        PopularitySegment segment, String key, RecommendationBasis basis, RecommendationReason reason
    ) {
    }
}
