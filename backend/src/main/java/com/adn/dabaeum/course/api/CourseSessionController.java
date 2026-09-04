package com.adn.dabaeum.course.api;

import com.adn.dabaeum.common.api.ApiResponse;
import com.adn.dabaeum.common.security.CurrentUserProvider;
import com.adn.dabaeum.common.web.RequestIdFilter;
import com.adn.dabaeum.course.application.CourseSessionApplicationService;
import com.adn.dabaeum.course.application.CourseSessionPage;
import com.adn.dabaeum.course.domain.CourseSession;
import com.adn.dabaeum.correction.api.CorrectionReasonRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class CourseSessionController {

    private final CourseSessionApplicationService service;
    private final CourseSessionApiMapper mapper;
    private final CurrentUserProvider currentUserProvider;

    public CourseSessionController(CourseSessionApplicationService service,
        CourseSessionApiMapper mapper, CurrentUserProvider currentUserProvider) {
        this.service = service;
        this.mapper = mapper;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping("/courses/{courseId}/sessions")
    public CourseSessionPageResponse list(@PathVariable UUID courseId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(defaultValue = "createdAt,desc") String sort,
        HttpServletRequest request) {
        CourseSessionPage result = service.list(mapper.toListQuery(courseId, page, size, sort));
        return mapper.toPageResponse(result, requestId(request));
    }

    @PostMapping("/courses/{courseId}/sessions")
    public ResponseEntity<ApiResponse<CourseSessionResponse>> create(@PathVariable UUID courseId,
        @Valid @RequestBody CourseSessionCreateRequest request, HttpServletRequest servletRequest) {
        CourseSession created = service.create(mapper.toCreateCommand(courseId, request),
            currentUserProvider.requireContext());
        return ResponseEntity.created(URI.create("/api/v1/sessions/" + created.id()))
            .body(mapper.toApiResponse(created, requestId(servletRequest)));
    }

    @GetMapping("/sessions/{sessionId}")
    public ApiResponse<CourseSessionResponse> get(@PathVariable UUID sessionId,
        HttpServletRequest request) {
        return mapper.toApiResponse(service.get(sessionId), requestId(request));
    }

    @PutMapping("/sessions/{sessionId}")
    public ApiResponse<CourseSessionResponse> update(@PathVariable UUID sessionId,
        @Valid @RequestBody CourseSessionUpdateRequest request, HttpServletRequest servletRequest) {
        CourseSession updated = service.update(mapper.toUpdateCommand(sessionId, request),
            currentUserProvider.requireContext());
        return mapper.toApiResponse(updated, requestId(servletRequest));
    }

    @PostMapping("/sessions/{sessionId}/reopen")
    public ApiResponse<CourseSessionResponse> reopen(
        @PathVariable UUID sessionId,
        @Valid @RequestBody CorrectionReasonRequest request,
        HttpServletRequest servletRequest
    ) {
        CourseSession reopened = service.reopen(
            sessionId, request.reason(), currentUserProvider.requireContext());
        return mapper.toApiResponse(reopened, requestId(servletRequest));
    }

    private String requestId(HttpServletRequest request) {
        return (String) request.getAttribute(RequestIdFilter.ATTRIBUTE_NAME);
    }
}
