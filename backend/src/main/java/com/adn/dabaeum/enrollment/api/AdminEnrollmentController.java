package com.adn.dabaeum.enrollment.api;

import com.adn.dabaeum.accesslog.domain.AccessLogAction;
import com.adn.dabaeum.accesslog.web.PersonalDataAccess;
import com.adn.dabaeum.common.api.KeywordQuery;
import com.adn.dabaeum.common.web.RequestIdFilter;
import com.adn.dabaeum.course.domain.CourseEducationType;
import com.adn.dabaeum.enrollment.application.EnrollmentQueryService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 스펙 #42 · 수강현황관리 화면. {@code /institutions/{id}/enrollments} 는 기관 단위라
 * 오너가 전 기관 수강신청을 한 목록으로 못 본다 — 이 컨트롤러가 사이트 전체를 준다.
 *
 * <p>인가는 여기서 하지 않는다 — {@code ApiAuthorizationRules} 의 규칙표가
 * {@code /api/v1/admin/*} 를 PLATFORM_ADMIN 전용으로 막는다.
 */
@RestController
@Profile({"local", "dev"})
@RequestMapping("/api/v1/admin")
public class AdminEnrollmentController {

    private final EnrollmentQueryService service;
    private final EnrollmentQueryApiMapper mapper;

    public AdminEnrollmentController(
        EnrollmentQueryService service, EnrollmentQueryApiMapper mapper
    ) {
        this.service = service;
        this.mapper = mapper;
    }

    @GetMapping("/enrollments")
    @PersonalDataAccess(action = AccessLogAction.READ, targetField = "userId")
    public InstitutionEnrollmentPageResponse listAll(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(defaultValue = "appliedAt,desc") String sort,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) UUID institutionId,
        @RequestParam(required = false) UUID courseId,
        @RequestParam(required = false) CourseEducationType educationType,
        @RequestParam(required = false) String q,
        HttpServletRequest servletRequest
    ) {
        EnrollmentQueryApiMapper.PageQuery query = mapper.toPageQuery(page, size, sort, status);
        var result = service.listAllEnrollments(
            query.status(), institutionId, courseId, educationType,
            KeywordQuery.normalize(q), query.page(), query.size(), query.sort());
        return mapper.toInstitutionPageResponse(result, requestId(servletRequest));
    }

    private String requestId(HttpServletRequest request) {
        return (String) request.getAttribute(RequestIdFilter.ATTRIBUTE_NAME);
    }
}
