package com.adn.dabaeum.inquiry.api;

import com.adn.dabaeum.accesslog.domain.AccessLogAction;
import com.adn.dabaeum.accesslog.web.PersonalDataAccess;
import com.adn.dabaeum.common.api.KeywordQuery;
import com.adn.dabaeum.common.web.RequestIdFilter;
import com.adn.dabaeum.course.domain.CourseEducationType;
import com.adn.dabaeum.inquiry.application.InquiryApplicationService;
import com.adn.dabaeum.inquiry.domain.InquiryStatus;
import com.adn.dabaeum.inquiry.domain.InquiryType;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 스펙 #42 · B-7 플랫폼 관리자 문의 관리. {@code /institutions/{id}/inquiries} 는 기관
 * 단위라 오너가 전 기관 문의를 한 목록으로 못 본다 — 이 컨트롤러가 사이트 전체를 준다.
 *
 * <p>인가는 여기서 하지 않는다 — {@code ApiAuthorizationRules} 의 규칙표가
 * {@code /api/v1/admin/*} 를 PLATFORM_ADMIN 전용으로 막는다. 응답은 기관 문의 목록과
 * 같은 매니저 시야(실명·연락처 노출)를 그대로 쓴다.
 */
@RestController
@Profile({"local", "dev"})
@RequestMapping("/api/v1/admin")
public class AdminInquiryController {

    private final InquiryApplicationService service;
    private final InquiryApiMapper mapper;

    public AdminInquiryController(InquiryApplicationService service, InquiryApiMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @GetMapping("/inquiries")
    @PersonalDataAccess(action = AccessLogAction.READ)
    public InquiryPageResponse listAll(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(required = false) InquiryStatus status,
        @RequestParam(required = false) InquiryType inquiryType,
        @RequestParam(required = false) CourseEducationType educationType,
        @RequestParam(required = false) UUID institutionId,
        @RequestParam(required = false) UUID courseId,
        @RequestParam(required = false) String q,
        HttpServletRequest servletRequest
    ) {
        mapper.validatePaging(page, size);
        var result = service.listAll(
            status, inquiryType, educationType, institutionId, courseId,
            KeywordQuery.normalize(q), page, size);
        return mapper.toPageResponse(result, requestId(servletRequest));
    }

    private String requestId(HttpServletRequest request) {
        return (String) request.getAttribute(RequestIdFilter.ATTRIBUTE_NAME);
    }
}
