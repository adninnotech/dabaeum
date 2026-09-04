package com.adn.dabaeum.course.api;

import com.adn.dabaeum.common.api.ApiResponse;
import com.adn.dabaeum.common.security.CurrentUserProvider;
import com.adn.dabaeum.common.web.RequestIdFilter;
import com.adn.dabaeum.course.application.CourseQueryService;
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
public class CourseQueryController {

    private final CourseQueryService service;
    private final CourseQueryApiMapper mapper;
    private final CourseApiMapper courseApiMapper;
    private final CurrentUserProvider currentUserProvider;

    public CourseQueryController(
        CourseQueryService service,
        CourseQueryApiMapper mapper,
        CourseApiMapper courseApiMapper,
        CurrentUserProvider currentUserProvider
    ) {
        this.service = service;
        this.mapper = mapper;
        this.courseApiMapper = courseApiMapper;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping("/instructors/me/courses")
    public InstructorCoursePageResponse listInstructorCourses(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(defaultValue = "createdAt,desc") String sort,
        @RequestParam(required = false) String status,
        HttpServletRequest servletRequest
    ) {
        CourseQueryApiMapper.CoursePageQuery query =
            mapper.toCoursePageQuery(page, size, sort, status);
        var result = service.listInstructorCourses(
            currentUserProvider.requireContext(), query.status(),
            query.page(), query.size(), query.sort());
        return mapper.toInstructorCoursePageResponse(result, requestId(servletRequest));
    }

    @GetMapping("/instructors/me/courses/stats")
    public ApiResponse<InstructorCourseStatsResponse> instructorCourseStats(
        HttpServletRequest servletRequest
    ) {
        var stats = service.instructorCourseStats(currentUserProvider.requireContext());
        return new ApiResponse<>(
            mapper.toStatsResponse(stats), mapper.meta(requestId(servletRequest)));
    }

    @GetMapping("/institutions/{institutionId}/courses")
    public CoursePageResponse listInstitutionCourses(
        @PathVariable UUID institutionId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(defaultValue = "createdAt,desc") String sort,
        @RequestParam(required = false) String status,
        HttpServletRequest servletRequest
    ) {
        CourseQueryApiMapper.CoursePageQuery query =
            mapper.toCoursePageQuery(page, size, sort, status);
        var result = service.listInstitutionCourses(
            currentUserProvider.requireContext(), institutionId, query.status(),
            query.page(), query.size(), query.sort());
        return courseApiMapper.toPageResponse(result, requestId(servletRequest));
    }

    @GetMapping("/institutions/{institutionId}/instructors")
    public InstitutionInstructorPageResponse listInstitutionInstructors(
        @PathVariable UUID institutionId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(defaultValue = "joinedAt,desc") String sort,
        @RequestParam(required = false) String status,
        HttpServletRequest servletRequest
    ) {
        CourseQueryApiMapper.InstructorPageQuery query =
            mapper.toInstructorPageQuery(page, size, sort, status);
        var result = service.listInstitutionInstructors(
            currentUserProvider.requireContext(), institutionId, query.status(),
            query.page(), query.size(), query.sort());
        return mapper.toInstitutionInstructorPageResponse(result, requestId(servletRequest));
    }

    private String requestId(HttpServletRequest request) {
        return (String) request.getAttribute(RequestIdFilter.ATTRIBUTE_NAME);
    }
}
