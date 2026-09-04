package com.adn.dabaeum.interest.api;

import com.adn.dabaeum.common.api.ApiErrorCode;
import com.adn.dabaeum.common.api.ApiException;
import com.adn.dabaeum.common.api.ApiMeta;
import com.adn.dabaeum.common.api.ApiResponse;
import com.adn.dabaeum.common.api.PageMeta;
import com.adn.dabaeum.common.security.CurrentUserProvider;
import com.adn.dabaeum.common.web.RequestIdFilter;
import com.adn.dabaeum.interest.application.CourseInterestApplicationService;
import com.adn.dabaeum.interest.application.CourseInterestPage;
import com.adn.dabaeum.interest.domain.CourseInterest;
import com.adn.dabaeum.interest.domain.CourseInterestView;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
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
public class CourseInterestController {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final CourseInterestApplicationService service;
    private final CurrentUserProvider currentUserProvider;
    private final Clock clock;

    public CourseInterestController(
        CourseInterestApplicationService service,
        CurrentUserProvider currentUserProvider,
        Clock clock
    ) {
        this.service = service;
        this.currentUserProvider = currentUserProvider;
        this.clock = clock;
    }

    @GetMapping("/users/me/interests")
    public InterestPageResponse listMine(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        HttpServletRequest servletRequest
    ) {
        if (page < 0 || size < 1 || size > 100 || page > Integer.MAX_VALUE / size) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST, ApiErrorCode.BAD_REQUEST,
                "Request parameter is invalid", List.of("page", "size"));
        }
        CourseInterestPage result = service.listMine(
            currentUserProvider.requireContext(), page, size);
        return new InterestPageResponse(
            result.data().stream().map(this::toResponse).toList(),
            new PageMeta(result.page(), result.size(),
                result.totalElements(), result.totalPages()),
            meta(requestId(servletRequest)));
    }

    @PostMapping("/users/me/interests")
    public ResponseEntity<ApiResponse<InterestResponse>> add(
        @RequestBody(required = false) InterestCreateRequest request,
        HttpServletRequest servletRequest
    ) {
        if (request == null || request.courseId() == null) {
            throw new ApiException(
                HttpStatus.UNPROCESSABLE_ENTITY, ApiErrorCode.VALIDATION_FAILED,
                "Request validation failed", List.of("courseId"));
        }
        CourseInterest interest = service.add(
            currentUserProvider.requireContext(), request.courseId());
        return ResponseEntity.status(HttpStatus.CREATED)
            .location(URI.create("/api/v1/users/me/interests/" + interest.id()))
            .body(new ApiResponse<>(
                new InterestResponse(
                    interest.id(), interest.courseId(), interest.createdAt(),
                    null, null, null, null, null),
                meta(requestId(servletRequest))));
    }

    @DeleteMapping("/users/me/interests/{interestId}")
    public ApiResponse<com.adn.dabaeum.common.api.DeletionResponse> remove(
        @PathVariable UUID interestId,
        HttpServletRequest servletRequest
    ) {
        service.remove(currentUserProvider.requireContext(), interestId);
        return new ApiResponse<>(
            new com.adn.dabaeum.common.api.DeletionResponse(interestId),
            meta(requestId(servletRequest)));
    }

    private InterestResponse toResponse(CourseInterestView view) {
        return new InterestResponse(
            view.id(), view.courseId(), view.createdAt(), view.courseTitle(),
            view.institutionName(), view.category(), view.educationType(),
            view.courseStatus());
    }

    private ApiMeta meta(String requestId) {
        return new ApiMeta(requestId, OffsetDateTime.ofInstant(clock.instant(), SEOUL));
    }

    private String requestId(HttpServletRequest request) {
        return (String) request.getAttribute(RequestIdFilter.ATTRIBUTE_NAME);
    }
}
