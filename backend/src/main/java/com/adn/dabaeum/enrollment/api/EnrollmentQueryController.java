package com.adn.dabaeum.enrollment.api;

import com.adn.dabaeum.common.security.CurrentUserProvider;
import com.adn.dabaeum.common.web.RequestIdFilter;
import com.adn.dabaeum.enrollment.application.EnrollmentQueryService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile({"local", "dev"})
@RequestMapping("/api/v1")
public class EnrollmentQueryController {

    private final EnrollmentQueryService service;
    private final EnrollmentQueryApiMapper mapper;
    private final CurrentUserProvider currentUserProvider;

    public EnrollmentQueryController(
        EnrollmentQueryService service,
        EnrollmentQueryApiMapper mapper,
        CurrentUserProvider currentUserProvider
    ) {
        this.service = service;
        this.mapper = mapper;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping("/users/me/enrollments")
    public MyEnrollmentPageResponse listMyEnrollments(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(defaultValue = "appliedAt,desc") String sort,
        @RequestParam(required = false) String status,
        HttpServletRequest servletRequest
    ) {
        EnrollmentQueryApiMapper.PageQuery query =
            mapper.toPageQuery(page, size, sort, status);
        var result = service.listMyEnrollments(
            currentUserProvider.requireContext(), query.status(),
            query.page(), query.size(), query.sort());
        return mapper.toMyPageResponse(result, requestId(servletRequest));
    }

    @GetMapping("/institutions/{institutionId}/enrollments")
    public InstitutionEnrollmentPageResponse listInstitutionEnrollments(
        @PathVariable UUID institutionId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(defaultValue = "appliedAt,desc") String sort,
        @RequestParam(required = false) String status,
        HttpServletRequest servletRequest
    ) {
        EnrollmentQueryApiMapper.PageQuery query =
            mapper.toPageQuery(page, size, sort, status);
        var result = service.listInstitutionEnrollments(
            currentUserProvider.requireContext(), institutionId, query.status(),
            query.page(), query.size(), query.sort());
        return mapper.toInstitutionPageResponse(result, requestId(servletRequest));
    }

    @GetMapping("/users/me/learning-summary")
    public com.adn.dabaeum.common.api.ApiResponse<LearningSummaryResponse> learningSummary(
        HttpServletRequest servletRequest
    ) {
        var summary = service.learningSummary(currentUserProvider.requireContext());
        return new com.adn.dabaeum.common.api.ApiResponse<>(
            new LearningSummaryResponse(
                summary.applying(), summary.inProgress(), summary.finished()),
            mapper.meta(requestId(servletRequest)));
    }

    @GetMapping("/users/me/learning-courses")
    public LearningCoursePageResponse listLearningCourses(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(required = false) String status,
        HttpServletRequest servletRequest
    ) {
        mapper.validatePaging(page, size);
        var result = service.listLearningCourses(
            currentUserProvider.requireContext(),
            mapper.parseLearningStatus(status), page, size);
        return mapper.toLearningCoursePageResponse(result, requestId(servletRequest));
    }

    @GetMapping("/instructors/me/enrollment-status")
    public EnrollmentProgressPageResponse listInstructorEnrollmentProgress(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(required = false) UUID courseId,
        HttpServletRequest servletRequest
    ) {
        mapper.validatePaging(page, size);
        var result = service.listInstructorEnrollmentProgress(
            currentUserProvider.requireContext(), courseId, page, size);
        return mapper.toProgressPageResponse(result, requestId(servletRequest));
    }

    @GetMapping("/enrollments/{enrollmentId}/progress")
    public com.adn.dabaeum.common.api.ApiResponse<EnrollmentProgressResponse>
        enrollmentProgress(
            @PathVariable UUID enrollmentId,
            HttpServletRequest servletRequest
    ) {
        var progress = service.enrollmentProgress(
            currentUserProvider.requireContext(), enrollmentId);
        return new com.adn.dabaeum.common.api.ApiResponse<>(
            mapper.toProgressResponse(progress),
            mapper.meta(requestId(servletRequest)));
    }

    private String requestId(HttpServletRequest request) {
        return (String) request.getAttribute(RequestIdFilter.ATTRIBUTE_NAME);
    }
}
