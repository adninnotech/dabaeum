package com.adn.dabaeum.enrollment.api;

import com.adn.dabaeum.accesslog.domain.AccessLogAction;
import com.adn.dabaeum.accesslog.web.PersonalDataAccess;
import com.adn.dabaeum.common.api.ApiResponse;
import com.adn.dabaeum.common.api.KeywordQuery;
import com.adn.dabaeum.common.web.RequestIdFilter;
import com.adn.dabaeum.enrollment.application.EnrollmentQueryService;
import com.adn.dabaeum.inquiry.api.InquiryApiMapper;
import com.adn.dabaeum.inquiry.api.InquiryPageResponse;
import com.adn.dabaeum.inquiry.application.InquiryApplicationService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * B-4 회원 상세가 쓰는 플랫폼 관리자 전용 학습 이력 조회. 같은 조회를 {@code /users/me/*} 가
 * 이미 하고 있어 서비스의 userId 판 메서드만 불러 쓴다 — 로직을 복제하지 않는다.
 *
 * <p>인가는 여기서 하지 않고 {@code ApiAuthorizationRules} 의 규칙표가 한다. 규칙표에서
 * {@code /users/*&#47;...} 와일드카드가 {@code /users/me/...} 보다 <b>뒤에</b> 와야 한다 —
 * 앞서면 학습자의 내 이력 조회가 403 이 된다.
 */
@RestController
@Profile({"local", "dev"})
@RequestMapping("/api/v1")
public class AdminUserLearningController {

    private final EnrollmentQueryService enrollmentQueryService;
    private final InquiryApplicationService inquiryService;
    private final EnrollmentQueryApiMapper mapper;
    private final InquiryApiMapper inquiryMapper;

    public AdminUserLearningController(
        EnrollmentQueryService enrollmentQueryService,
        InquiryApplicationService inquiryService,
        EnrollmentQueryApiMapper mapper,
        InquiryApiMapper inquiryMapper
    ) {
        this.enrollmentQueryService = enrollmentQueryService;
        this.inquiryService = inquiryService;
        this.mapper = mapper;
        this.inquiryMapper = inquiryMapper;
    }

    @GetMapping("/users/{userId}/enrollments")
    @PersonalDataAccess(action = AccessLogAction.READ)
    public MyEnrollmentPageResponse listUserEnrollments(
        @PathVariable UUID userId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(defaultValue = "appliedAt,desc") String sort,
        @RequestParam(required = false) String status,
        HttpServletRequest servletRequest
    ) {
        EnrollmentQueryApiMapper.PageQuery query =
            mapper.toPageQuery(page, size, sort, status);
        var result = enrollmentQueryService.listEnrollmentsOf(
            userId, query.status(), query.page(), query.size(), query.sort());
        return mapper.toMyPageResponse(result, requestId(servletRequest));
    }

    @GetMapping("/users/{userId}/learning-courses")
    @PersonalDataAccess(action = AccessLogAction.READ)
    public LearningCoursePageResponse listUserLearningCourses(
        @PathVariable UUID userId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) String q,
        HttpServletRequest servletRequest
    ) {
        mapper.validatePaging(page, size);
        var result = enrollmentQueryService.listLearningCoursesOf(
            userId, mapper.parseLearningStatus(status),
            KeywordQuery.normalize(q), page, size);
        return mapper.toLearningCoursePageResponse(result, requestId(servletRequest));
    }

    @GetMapping("/users/{userId}/inquiries")
    @PersonalDataAccess(action = AccessLogAction.READ)
    public InquiryPageResponse listUserInquiries(
        @PathVariable UUID userId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) String q,
        HttpServletRequest servletRequest
    ) {
        inquiryMapper.validatePaging(page, size);
        var result = inquiryService.listOf(
            userId, inquiryMapper.parseStatus(status),
            KeywordQuery.normalize(q), page, size);
        return inquiryMapper.toPageResponse(result, requestId(servletRequest));
    }

    @GetMapping("/users/{userId}/learning-stats")
    @PersonalDataAccess(action = AccessLogAction.READ)
    public ApiResponse<UserLearningStatsResponse> getUserLearningStats(
        @PathVariable UUID userId,
        HttpServletRequest servletRequest
    ) {
        var stats = enrollmentQueryService.learningStatsOf(userId);
        return new ApiResponse<>(
            new UserLearningStatsResponse(
                stats.totalCount(), stats.inProgressCount(), stats.completedCount(),
                stats.totalStudyMinutes(), stats.averageProgressPercent(),
                stats.inquiryCount()),
            mapper.meta(requestId(servletRequest)));
    }

    private String requestId(HttpServletRequest request) {
        return (String) request.getAttribute(RequestIdFilter.ATTRIBUTE_NAME);
    }
}
