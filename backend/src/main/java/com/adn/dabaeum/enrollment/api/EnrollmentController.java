package com.adn.dabaeum.enrollment.api;

import com.adn.dabaeum.common.api.ApiResponse;
import com.adn.dabaeum.common.security.CurrentUserProvider;
import com.adn.dabaeum.common.web.RequestIdFilter;
import com.adn.dabaeum.enrollment.application.EnrollmentApplicationService;
import com.adn.dabaeum.enrollment.application.EnrollmentPage;
import com.adn.dabaeum.enrollment.domain.Enrollment;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class EnrollmentController {

    private final EnrollmentApplicationService service;
    private final EnrollmentApiMapper mapper;
    private final CurrentUserProvider currentUserProvider;

    public EnrollmentController(EnrollmentApplicationService service,
        EnrollmentApiMapper mapper, CurrentUserProvider currentUserProvider) {
        this.service = service;
        this.mapper = mapper;
        this.currentUserProvider = currentUserProvider;
    }

    @PostMapping("/courses/{courseId}/enrollments")
    public ResponseEntity<ApiResponse<EnrollmentResponse>> createSelf(
        @PathVariable UUID courseId,
        @Valid @RequestBody EnrollmentCreateRequest request,
        HttpServletRequest servletRequest
    ) {
        Enrollment enrollment = service.createSelf(
            mapper.toCreateCommand(courseId, request), currentUserProvider.requireContext());
        return ResponseEntity.created(URI.create("/api/v1/enrollments/" + enrollment.id()))
            .body(mapper.toApiResponse(enrollment, requestId(servletRequest)));
    }

    @PostMapping("/courses/{courseId}/proxy-enrollments")
    public ResponseEntity<ApiResponse<EnrollmentResponse>> createProxy(
        @PathVariable UUID courseId,
        @Valid @RequestBody ProxyEnrollmentCreateRequest request,
        HttpServletRequest servletRequest
    ) {
        Enrollment enrollment = service.createProxy(
            mapper.toProxyCommand(courseId, request), currentUserProvider.requireContext());
        return ResponseEntity.created(URI.create("/api/v1/enrollments/" + enrollment.id()))
            .body(mapper.toApiResponse(enrollment, requestId(servletRequest)));
    }

    @GetMapping("/courses/{courseId}/enrollments")
    public EnrollmentPageResponse list(
        @PathVariable UUID courseId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(defaultValue = "createdAt,desc") String sort,
        HttpServletRequest request
    ) {
        EnrollmentPage result = service.list(mapper.toListQuery(courseId, page, size, sort),
            currentUserProvider.requireContext());
        return mapper.toPageResponse(result, requestId(request));
    }

    @GetMapping("/enrollments/{enrollmentId}")
    public ApiResponse<EnrollmentResponse> get(
        @PathVariable UUID enrollmentId,
        HttpServletRequest request
    ) {
        return mapper.toApiResponse(service.get(enrollmentId, currentUserProvider.requireContext()),
            requestId(request));
    }

    @PostMapping("/enrollments/{enrollmentId}/approve")
    public ApiResponse<EnrollmentResponse> approve(
        @PathVariable UUID enrollmentId,
        HttpServletRequest request
    ) {
        requireNoBody(request);
        return mapper.toApiResponse(
            service.approve(enrollmentId, currentUserProvider.requireContext()),
            requestId(request));
    }

    @PostMapping("/enrollments/{enrollmentId}/reject")
    public ApiResponse<EnrollmentResponse> reject(
        @PathVariable UUID enrollmentId,
        @Valid @RequestBody EnrollmentRejectionRequest rejection,
        HttpServletRequest request
    ) {
        return mapper.toApiResponse(
            service.reject(mapper.toRejectCommand(enrollmentId, rejection),
                currentUserProvider.requireContext()),
            requestId(request));
    }

    @PostMapping("/enrollments/{enrollmentId}/cancel")
    public ApiResponse<EnrollmentResponse> cancel(
        @PathVariable UUID enrollmentId,
        HttpServletRequest request
    ) {
        requireNoBody(request);
        return mapper.toApiResponse(
            service.cancel(enrollmentId, currentUserProvider.requireContext()),
            requestId(request));
    }

    @PostMapping("/enrollments/{enrollmentId}/withdraw")
    public ApiResponse<EnrollmentResponse> withdraw(
        @PathVariable UUID enrollmentId,
        HttpServletRequest request
    ) {
        requireNoBody(request);
        return mapper.toApiResponse(
            service.withdraw(enrollmentId, currentUserProvider.requireContext()),
            requestId(request));
    }

    private void requireNoBody(HttpServletRequest request) {
        if (request.getContentLengthLong() > 0) {
            throw new com.adn.dabaeum.common.api.ApiException(
                org.springframework.http.HttpStatus.BAD_REQUEST,
                com.adn.dabaeum.common.api.ApiErrorCode.BAD_REQUEST,
                "Request body is not allowed");
        }
    }

    private String requestId(HttpServletRequest request) {
        return (String) request.getAttribute(RequestIdFilter.ATTRIBUTE_NAME);
    }
}
