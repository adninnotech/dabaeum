package com.adn.dabaeum.review.api;

import com.adn.dabaeum.common.api.ApiErrorCode;
import com.adn.dabaeum.common.api.ApiException;
import com.adn.dabaeum.common.api.ApiMeta;
import com.adn.dabaeum.common.api.ApiResponse;
import com.adn.dabaeum.common.api.PageMeta;
import com.adn.dabaeum.common.security.CurrentUserProvider;
import com.adn.dabaeum.common.web.RequestIdFilter;
import com.adn.dabaeum.review.application.CourseReviewApplicationService;
import com.adn.dabaeum.review.application.CourseReviewPage;
import com.adn.dabaeum.review.domain.CourseReview;
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

    public CourseReviewController(
        CourseReviewApplicationService service,
        CurrentUserProvider currentUserProvider,
        Clock clock
    ) {
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
        if (content != null && content.length() > 2000) {
            invalid.add("content");
        }
        if (!invalid.isEmpty()) {
            throw new ApiException(
                HttpStatus.UNPROCESSABLE_ENTITY, ApiErrorCode.VALIDATION_FAILED,
                "Request validation failed", invalid);
        }
        CourseReview review = service.create(
            currentUserProvider.requireContext(), courseId, rating,
            content == null || content.isEmpty() ? null : content);
        return ResponseEntity.status(HttpStatus.CREATED)
            .location(URI.create("/api/v1/courses/" + courseId + "/reviews"))
            .body(new ApiResponse<>(
                new ReviewResponse(
                    review.id(), review.courseId(), review.rating(), review.content(),
                    review.createdAt(), null, null),
                meta(requestId(servletRequest))));
    }

    @GetMapping("/users/me/reviews")
    public ReviewPageResponse listMine(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        HttpServletRequest servletRequest
    ) {
        validatePaging(page, size);
        return toPageResponse(
            service.listMine(currentUserProvider.requireContext(), page, size),
            requestId(servletRequest));
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
            service.listByCourse(courseId, page, size), requestId(servletRequest));
    }

    private ReviewPageResponse toPageResponse(CourseReviewPage page, String requestId) {
        return new ReviewPageResponse(
            page.data().stream().map(this::toResponse).toList(),
            new PageMeta(page.page(), page.size(), page.totalElements(), page.totalPages()),
            meta(requestId));
    }

    private ReviewResponse toResponse(CourseReviewView view) {
        return new ReviewResponse(
            view.id(), view.courseId(), view.rating(), view.content(),
            view.createdAt(), view.courseTitle(), view.userName());
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
}
