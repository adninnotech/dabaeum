package com.adn.dabaeum.course.api;

import com.adn.dabaeum.common.api.ApiResponse;
import com.adn.dabaeum.common.security.CurrentUserProvider;
import com.adn.dabaeum.common.web.RequestIdFilter;
import com.adn.dabaeum.course.application.CourseApplicationService;
import com.adn.dabaeum.course.application.CoursePage;
import com.adn.dabaeum.course.domain.Course;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/courses")
public class CourseController {

    private final CourseApplicationService service;
    private final CourseApiMapper mapper;
    private final CurrentUserProvider currentUserProvider;

    public CourseController(
        CourseApplicationService service,
        CourseApiMapper mapper,
        CurrentUserProvider currentUserProvider
    ) {
        this.service = service;
        this.mapper = mapper;
        this.currentUserProvider = currentUserProvider;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<CourseResponse>> create(
        @Valid @RequestBody CourseCreateRequest request,
        HttpServletRequest servletRequest
    ) {
        Course course = service.create(
            mapper.toCommand(request),
            currentUserProvider.requireContext()
        );
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{courseId}")
            .buildAndExpand(course.id())
            .toUri();
        return ResponseEntity.created(location)
            .body(mapper.toApiResponse(course, requestId(servletRequest)));
    }

    @GetMapping("/{courseId}")
    public ApiResponse<CourseResponse> get(
        @PathVariable UUID courseId,
        HttpServletRequest servletRequest
    ) {
        return mapper.toApiResponse(
            service.get(courseId),
            requestId(servletRequest)
        );
    }

    @GetMapping
    public CoursePageResponse list(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(defaultValue = "createdAt,desc") String sort,
        HttpServletRequest servletRequest
    ) {
        CoursePage result = service.list(mapper.toListQuery(page, size, sort));
        return mapper.toPageResponse(result, requestId(servletRequest));
    }

    @PutMapping("/{courseId}")
    public ApiResponse<CourseResponse> update(
        @PathVariable UUID courseId,
        @Valid @RequestBody CourseUpdateRequest request,
        HttpServletRequest servletRequest
    ) {
        Course updated = service.update(
            mapper.toUpdateCommand(courseId, request),
            currentUserProvider.requireContext()
        );
        return mapper.toApiResponse(updated, requestId(servletRequest));
    }

    @PostMapping("/{courseId}/publish")
    public ApiResponse<CourseResponse> publish(
        @PathVariable UUID courseId,
        HttpServletRequest servletRequest
    ) {
        Course published = service.publish(
            courseId,
            currentUserProvider.requireContext()
        );
        return mapper.toApiResponse(published, requestId(servletRequest));
    }

    @PostMapping("/{courseId}/close")
    public ApiResponse<CourseResponse> close(
        @PathVariable UUID courseId,
        HttpServletRequest servletRequest
    ) {
        Course closed = service.close(
            courseId,
            currentUserProvider.requireContext()
        );
        return mapper.toApiResponse(closed, requestId(servletRequest));
    }

    @PostMapping("/{courseId}/reopen")
    public ApiResponse<CourseResponse> reopen(
        @PathVariable UUID courseId,
        @Valid @RequestBody CorrectionReasonRequest request,
        HttpServletRequest servletRequest
    ) {
        Course reopened = service.reopen(
            courseId, request.reason(), currentUserProvider.requireContext());
        return mapper.toApiResponse(reopened, requestId(servletRequest));
    }

    private String requestId(HttpServletRequest request) {
        return (String) request.getAttribute(RequestIdFilter.ATTRIBUTE_NAME);
    }
}
