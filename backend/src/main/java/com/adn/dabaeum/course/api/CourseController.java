package com.adn.dabaeum.course.api;

import com.adn.dabaeum.common.api.ApiResponse;
import com.adn.dabaeum.common.api.DeletionResponse;
import com.adn.dabaeum.common.security.CurrentUserProvider;
import com.adn.dabaeum.common.web.RequestIdFilter;
import com.adn.dabaeum.course.application.CourseApplicationService;
import com.adn.dabaeum.course.application.CoursePage;
import com.adn.dabaeum.course.domain.Course;
import com.adn.dabaeum.course.domain.CourseEducationType;
import com.adn.dabaeum.course.domain.CourseSelectionMethod;
import com.adn.dabaeum.course.domain.CourseStatus;
import com.adn.dabaeum.correction.api.CorrectionReasonRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
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

    private final CourseApplicationService courseApplicationService;
    private final CourseApiMapper courseApiMapper;
    private final CurrentUserProvider currentUserProvider;

    public CourseController(
        CourseApplicationService courseApplicationService,
        CourseApiMapper courseApiMapper,
        CurrentUserProvider currentUserProvider
    ) {
        this.courseApplicationService = courseApplicationService;
        this.courseApiMapper = courseApiMapper;
        this.currentUserProvider = currentUserProvider;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<CourseResponse>> createCourse(
        @Valid @RequestBody CourseCreateRequest request,
        HttpServletRequest servletRequest
    ) {
        Course course = courseApplicationService.create(
            courseApiMapper.toCommand(request),
            currentUserProvider.requireContext()
        );
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{courseId}")
            .buildAndExpand(course.id())
            .toUri();

        return ResponseEntity.created(location)
            .body(courseApiMapper.toApiResponse(course, requestId(servletRequest)));
    }

    @GetMapping("/{courseId}")
    public ApiResponse<CourseResponse> getCourse(
        @PathVariable UUID courseId,
        HttpServletRequest servletRequest
    ) {
        return courseApiMapper.toApiResponse(
            courseApplicationService.get(courseId, currentUserProvider.findContext().orElse(null)),
            requestId(servletRequest)
        );
    }

    @GetMapping
    public CoursePageResponse getCourses(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(defaultValue = "createdAt,desc") String sort,
        @RequestParam(required = false) String q,
        @RequestParam(required = false) CourseEducationType educationType,
        @RequestParam(required = false) CourseSelectionMethod selectionMethod,
        @RequestParam(required = false) String classificationCode,
        @RequestParam(required = false) CourseStatus status,
        HttpServletRequest servletRequest
    ) {
        CoursePage result = courseApplicationService.list(
            courseApiMapper.toListQuery(page, size, sort, q, educationType, selectionMethod, status, classificationCode),
            currentUserProvider.findContext().orElse(null)
        );

        return courseApiMapper.toPageResponse(result, requestId(servletRequest));
    }

    @PutMapping("/{courseId}")
    public ApiResponse<CourseResponse> updateCourse(
        @PathVariable UUID courseId,
        @Valid @RequestBody CourseUpdateRequest request,
        HttpServletRequest servletRequest
    ) {
        courseApplicationService.update(
            courseApiMapper.toUpdateCommand(courseId, request),
            currentUserProvider.requireContext()
        );

        // enrolledCount 는 Course 에 없다 — 갱신 후 뷰를 다시 읽어 실제 값을 응답에 싣는다.
        return courseApiMapper.toApiResponse(
            courseApplicationService.get(courseId, currentUserProvider.requireContext()),
            requestId(servletRequest)
        );
    }

    @PostMapping("/{courseId}/publish")
    public ApiResponse<CourseResponse> publish(
        @PathVariable UUID courseId,
        HttpServletRequest servletRequest
    ) {
        courseApplicationService.publish(
            courseId,
            currentUserProvider.requireContext()
        );

        return courseApiMapper.toApiResponse(
            courseApplicationService.get(courseId, currentUserProvider.requireContext()),
            requestId(servletRequest)
        );
    }

    @PostMapping("/{courseId}/close")
    public ApiResponse<CourseResponse> close(
        @PathVariable UUID courseId,
        HttpServletRequest servletRequest
    ) {
        courseApplicationService.close(
            courseId,
            currentUserProvider.requireContext()
        );

        return courseApiMapper.toApiResponse(
            courseApplicationService.get(courseId, currentUserProvider.requireContext()),
            requestId(servletRequest)
        );
    }

    @PostMapping("/{courseId}/reopen")
    public ApiResponse<CourseResponse> reopen(
        @PathVariable UUID courseId,
        @Valid @RequestBody CorrectionReasonRequest request,
        HttpServletRequest servletRequest
    ) {
        courseApplicationService.reopen(
            courseId, request.reason(), currentUserProvider.requireContext()
        );

        return courseApiMapper.toApiResponse(
            courseApplicationService.get(courseId, currentUserProvider.requireContext()),
            requestId(servletRequest)
        );
    }

    /** 임시저장(DRAFT) 강좌만 지운다. 그 밖의 상태는 409 COURSE_STATUS_CONFLICT. */
    @DeleteMapping("/{courseId}")
    public ApiResponse<DeletionResponse> deleteDraft(
        @PathVariable UUID courseId,
        HttpServletRequest servletRequest
    ) {
        courseApplicationService.deleteDraft(courseId, currentUserProvider.requireContext());
        return courseApiMapper.toDeletionResponse(courseId, requestId(servletRequest));
    }

    private String requestId(HttpServletRequest request) {
        return request.getAttribute(RequestIdFilter.ATTRIBUTE_NAME).toString();
    }
}
