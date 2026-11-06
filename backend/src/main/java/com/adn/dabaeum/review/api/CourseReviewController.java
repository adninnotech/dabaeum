package com.adn.dabaeum.review.api;

import org.springframework.beans.factory.ObjectProvider;
import com.adn.dabaeum.classification.application.CourseClassificationService;
import com.adn.dabaeum.common.api.ApiErrorCode;
import com.adn.dabaeum.common.api.ApiException;
import com.adn.dabaeum.common.api.ApiMeta;
import com.adn.dabaeum.common.api.ApiResponse;
import com.adn.dabaeum.common.api.PageMeta;
import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import com.adn.dabaeum.common.security.CurrentUserProvider;
import com.adn.dabaeum.common.text.NameMask;
import com.adn.dabaeum.common.web.RequestIdFilter;
import com.adn.dabaeum.enrollment.api.CourseCardFields;
import com.adn.dabaeum.review.application.CourseReviewApplicationService;
import com.adn.dabaeum.review.application.CourseReviewPage;
import com.adn.dabaeum.review.domain.CourseReview;
import com.adn.dabaeum.review.domain.CourseReviewSummary;
import com.adn.dabaeum.review.domain.CourseReviewView;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile({"local", "dev"})
@RequestMapping("/api/v1")
public class CourseReviewController {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final CourseReviewApplicationService service;
    private final CurrentUserProvider currentUserProvider;
    private final Clock clock;

    // 분류 서비스는 local/dev 에만 있다. 없으면(컨트롤러 테스트) 경로는 빈 배열.
    private final ObjectProvider<CourseClassificationService> classifications;

    public CourseReviewController(
        CourseReviewApplicationService service,
        CurrentUserProvider currentUserProvider,
        Clock clock,
        ObjectProvider<CourseClassificationService> classifications
    ) {
        this.classifications = classifications;
        this.service = service;
        this.currentUserProvider = currentUserProvider;
        this.clock = clock;
    }

    @PostMapping("/courses/{courseId}/reviews")
    public ResponseEntity<ApiResponse<ReviewResponse>> create(
        @PathVariable UUID courseId,
        @RequestBody(required = false) ReviewCreateRequest request,
        HttpServletRequest servletRequest
    ) {
        List<String> invalid = new ArrayList<>();
        Integer rating = request == null ? null : request.rating();
        String content = request == null || request.content() == null
            ? null : request.content().trim();
        if (rating == null || rating < 1 || rating > 5) {
            invalid.add("rating");
        }
        // 내용은 필수다(QA VAL-03). 등록 후 수정·삭제가 없어 빈 수강평이 남지 않게 서버가 막는다.
        if (content == null || content.isEmpty() || content.length() > 1000) {
            invalid.add("content");
        }
        if (!invalid.isEmpty()) {
            throw new ApiException(
                HttpStatus.UNPROCESSABLE_CONTENT, ApiErrorCode.VALIDATION_FAILED,
                "Request validation failed", invalid);
        }
        CourseReview review = service.create(
            currentUserProvider.requireContext(), courseId, rating, content);
        return ResponseEntity.status(HttpStatus.CREATED)
            .location(URI.create("/api/v1/courses/" + courseId + "/reviews"))
            .body(new ApiResponse<>(
                new ReviewResponse(
                    review.id(), review.courseId(), review.rating(), review.content(),
                    review.createdAt(), null, null, emptyCard()),
                meta(requestId(servletRequest))));
    }

    @GetMapping("/users/me/reviews")
    public ReviewPageResponse listMine(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(required = false) String q,
        HttpServletRequest servletRequest
    ) {
        validatePaging(page, size);
        // 본인 목록이다 — 자기 이름을 마스킹해서 보여줄 이유가 없다.
        return toPageResponse(
            service.listMine(
                currentUserProvider.requireContext(),
                com.adn.dabaeum.common.api.KeywordQuery.normalize(q), page, size),
            requestId(servletRequest), false);
    }

    @GetMapping("/courses/{courseId}/reviews")
    public ReviewPageResponse listByCourse(
        @PathVariable UUID courseId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        HttpServletRequest servletRequest
    ) {
        validatePaging(page, size);
        return toPageResponse(
            service.listByCourse(courseId, page, size), requestId(servletRequest), true);
    }

    @GetMapping("/courses/{courseId}/reviews/summary")
    public ApiResponse<ReviewSummaryResponse> courseSummary(
        @PathVariable UUID courseId,
        HttpServletRequest servletRequest
    ) {
        CourseReviewSummary summary = service.summarizeByCourse(courseId);
        return new ApiResponse<>(
            ReviewSummaryResponse.of(summary, null), meta(requestId(servletRequest)));
    }

    @GetMapping("/users/me/reviews/summary")
    public ApiResponse<ReviewSummaryResponse> mySummary(HttpServletRequest servletRequest) {
        AuthenticatedUserContext actor = currentUserProvider.requireContext();
        CourseReviewSummary summary = service.summarizeMine(actor);
        long writableCount = service.countWritableReviews(actor);
        return new ApiResponse<>(
            ReviewSummaryResponse.of(summary, writableCount), meta(requestId(servletRequest)));
    }

    private ReviewPageResponse toPageResponse(
        CourseReviewPage page, String requestId, boolean maskAuthor
    ) {
        return new ReviewPageResponse(
            page.data().stream().map(view -> toResponse(view, maskAuthor)).toList(),
            new PageMeta(page.page(), page.size(), page.totalElements(), page.totalPages()),
            meta(requestId));
    }

    private ReviewResponse toResponse(CourseReviewView view, boolean maskAuthor) {
        return new ReviewResponse(
            view.id(), view.courseId(), view.rating(), view.content(),
            view.createdAt(), view.courseTitle(),
            maskAuthor ? NameMask.mask(view.userName()) : view.userName(),
            new CourseCardFields(
                view.institutionName(), view.instructorName(), view.classificationCode(),
                classificationPath(view.classificationCode()),
                view.educationType(), view.sessionCount(), view.tags(),
                view.tuitionType(), view.tuitionAmount(), view.thumbnailFileId()));
    }

    private static CourseCardFields emptyCard() {
        return new CourseCardFields(
            null, null, null, List.of(), null, null, null, null, null, null);
    }

    private void validatePaging(int page, int size) {
        if (page < 0 || size < 1 || size > 100 || page > Integer.MAX_VALUE / size) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST, ApiErrorCode.BAD_REQUEST,
                "Request parameter is invalid", List.of("page", "size"));
        }
    }

    private ApiMeta meta(String requestId) {
        return new ApiMeta(requestId, OffsetDateTime.ofInstant(clock.instant(), SEOUL));
    }

    private String requestId(HttpServletRequest request) {
        return (String) request.getAttribute(RequestIdFilter.ATTRIBUTE_NAME);
    }
    private List<String> classificationPath(String code) {
        CourseClassificationService service = classifications.getIfAvailable();
        return service == null ? List.of() : service.pathOf(code);
    }
}
