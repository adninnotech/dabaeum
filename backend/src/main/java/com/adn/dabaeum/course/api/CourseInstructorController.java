package com.adn.dabaeum.course.api;

import com.adn.dabaeum.common.api.ApiResponse;
import com.adn.dabaeum.common.security.CurrentUserProvider;
import com.adn.dabaeum.common.web.RequestIdFilter;
import com.adn.dabaeum.course.application.CourseInstructorApplicationService;
import com.adn.dabaeum.course.application.CourseInstructorView;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/courses/{courseId}/instructors")
public class CourseInstructorController {

    private final CourseInstructorApplicationService service;
    private final CourseInstructorApiMapper mapper;
    private final CurrentUserProvider currentUserProvider;

    public CourseInstructorController(
        CourseInstructorApplicationService service,
        CourseInstructorApiMapper mapper,
        CurrentUserProvider currentUserProvider
    ) {
        this.service = service;
        this.mapper = mapper;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping
    public ApiResponse<List<CourseInstructorResponse>> list(
        @PathVariable UUID courseId,
        HttpServletRequest servletRequest
    ) {
        return mapper.toListResponse(service.list(courseId), requestId(servletRequest));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<CourseInstructorResponse>> assign(
        @PathVariable UUID courseId,
        @Valid @RequestBody CourseInstructorAssignRequest request,
        HttpServletRequest servletRequest
    ) {
        CourseInstructorView result = service.assign(
            mapper.toAssignCommand(courseId, request),
            currentUserProvider.requireContext()
        );
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{userId}")
            .buildAndExpand(result.userId())
            .toUri();
        return ResponseEntity.created(location)
            .body(mapper.toApiResponse(result, requestId(servletRequest)));
    }

    @PutMapping("/{userId}")
    public ApiResponse<CourseInstructorResponse> update(
        @PathVariable UUID courseId,
        @PathVariable UUID userId,
        @Valid @RequestBody CourseInstructorUpdateRequest request,
        HttpServletRequest servletRequest
    ) {
        return mapper.toApiResponse(
            service.updateRole(
                mapper.toUpdateCommand(courseId, userId, request),
                currentUserProvider.requireContext()
            ),
            requestId(servletRequest)
        );
    }

    @DeleteMapping("/{userId}")
    public ApiResponse<CourseInstructorResponse> remove(
        @PathVariable UUID courseId,
        @PathVariable UUID userId,
        HttpServletRequest servletRequest
    ) {
        return mapper.toApiResponse(
            service.remove(courseId, userId, currentUserProvider.requireContext()),
            requestId(servletRequest)
        );
    }

    private String requestId(HttpServletRequest request) {
        return (String) request.getAttribute(RequestIdFilter.ATTRIBUTE_NAME);
    }
}
