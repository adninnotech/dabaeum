package com.adn.dabaeum.recommendation.api;

import com.adn.dabaeum.common.api.ApiErrorCode;
import com.adn.dabaeum.common.api.ApiException;
import com.adn.dabaeum.common.api.ApiMeta;
import com.adn.dabaeum.common.api.ApiResponse;
import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import com.adn.dabaeum.common.security.CurrentUserProvider;
import com.adn.dabaeum.common.web.RequestIdFilter;
import com.adn.dabaeum.course.api.CourseApiMapper;
import com.adn.dabaeum.recommendation.application.CourseRecommendationService;
import com.adn.dabaeum.recommendation.application.RecommendationResult;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 홈 추천 강좌 (docs/specs/course-recommendation.md §6). 인증 없이 부를 수 있고, 로그인 학습자면 찜·신청으로 개인화한다.
 * exclude 는 홈 인기 섹션에 이미 그린 강좌 — 두 섹션에 같은 강좌가 두 번 나오지 않게 한다.
 */
@RestController
@Profile({"local", "dev"})
@RequestMapping("/api/v1/courses/recommended")
public class CourseRecommendationController {

    private final CourseRecommendationService service;
    private final CourseApiMapper courseMapper;
    private final CurrentUserProvider currentUserProvider;
    private final Clock clock;

    public CourseRecommendationController(
        CourseRecommendationService service,
        CourseApiMapper courseMapper,
        CurrentUserProvider currentUserProvider,
        Clock clock
    ) {
        this.service = service;
        this.courseMapper = courseMapper;
        this.currentUserProvider = currentUserProvider;
        this.clock = clock;
    }

    @GetMapping
    public ApiResponse<RecommendationListResponse> recommended(
        @RequestParam(required = false) Integer limit,
        @RequestParam(required = false) String exclude,
        HttpServletRequest request
    ) {
        RecommendationResult result = service.recommend(viewer(), parseExclude(exclude), limit);
        List<RecommendedCourseResponse> items = result.items().stream()
            .map(item -> new RecommendedCourseResponse(item.impressionId(), courseMapper.toResponse(item.course()),
                item.matchLevel(), RecommendationReasonResponse.from(item.reason())))
            .toList();
        return new ApiResponse<>(new RecommendationListResponse(result.basis(), items), meta(request));
    }

    @PostMapping("/{impressionId}/click")
    public ApiResponse<RecommendationClickAcceptedResponse> click(
        @PathVariable UUID impressionId,
        HttpServletRequest request
    ) {
        service.click(impressionId, currentUserProvider.requireContext());
        return new ApiResponse<>(new RecommendationClickAcceptedResponse(true), meta(request));
    }

    private Optional<AuthenticatedUserContext> viewer() {
        try {
            return currentUserProvider.findContext();
        } catch (ApiException notAUserPrincipal) {
            // dev 고정 토큰처럼 사용자 문맥이 없는 인증은 비로그인으로 본다.
            return Optional.empty();
        }
    }

    /** 쉼표 구분 강좌 id. UUID 가 아니면 400. */
    private static List<UUID> parseExclude(String raw) {
        if (raw == null) {
            return List.of();
        }
        try {
            return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(UUID::fromString)
                .toList();
        } catch (IllegalArgumentException malformed) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ApiErrorCode.BAD_REQUEST,
                "Invalid recommendation query", List.of("exclude"));
        }
    }

    private ApiMeta meta(HttpServletRequest request) {
        return new ApiMeta(
            (String) request.getAttribute(RequestIdFilter.ATTRIBUTE_NAME),
            OffsetDateTime.ofInstant(clock.instant(), ZoneId.of("Asia/Seoul")));
    }
}
