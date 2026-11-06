package com.adn.dabaeum.course.api;

import com.adn.dabaeum.common.api.ApiResponse;
import com.adn.dabaeum.common.security.CurrentUserProvider;
import com.adn.dabaeum.common.web.RequestIdFilter;
import com.adn.dabaeum.course.application.CourseAttachmentApplicationService;
import com.adn.dabaeum.course.domain.CourseAttachment;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/courses/{courseId}/attachments")
public class CourseAttachmentController {

    private final CourseAttachmentApplicationService service;
    private final CourseAttachmentApiMapper mapper;
    private final CurrentUserProvider currentUserProvider;

    public CourseAttachmentController(
        CourseAttachmentApplicationService service,
        CourseAttachmentApiMapper mapper,
        CurrentUserProvider currentUserProvider
    ) {
        this.service = service;
        this.mapper = mapper;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping
    public ApiResponse<List<CourseAttachmentResponse>> list(
        @PathVariable UUID courseId,
        HttpServletRequest servletRequest
    ) {
        return mapper.toListResponse(service.list(courseId), requestId(servletRequest));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<CourseAttachmentResponse>> attach(
        @PathVariable UUID courseId,
        @Valid @RequestBody CourseAttachmentAttachRequest request,
        HttpServletRequest servletRequest
    ) {
        CourseAttachment attached = service.attach(
            mapper.toAttachCommand(courseId, request),
            currentUserProvider.requireContext()
        );
        return ResponseEntity
            .created(URI.create(
                "/api/v1/courses/" + courseId + "/attachments/" + attached.id()))
            .body(mapper.toApiResponse(attached, requestId(servletRequest)));
    }

    @DeleteMapping("/{attachmentId}")
    public ApiResponse<CourseAttachmentResponse> remove(
        @PathVariable UUID courseId,
        @PathVariable UUID attachmentId,
        HttpServletRequest servletRequest
    ) {
        CourseAttachment removed = service.remove(
            courseId, attachmentId, currentUserProvider.requireContext());
        return mapper.toApiResponse(removed, requestId(servletRequest));
    }

    private String requestId(HttpServletRequest request) {
        return (String) request.getAttribute(RequestIdFilter.ATTRIBUTE_NAME);
    }
}
