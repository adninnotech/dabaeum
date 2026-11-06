package com.adn.dabaeum.accesslog.api;

import com.adn.dabaeum.accesslog.application.AdminAccessLogQueryService;
import com.adn.dabaeum.accesslog.domain.AccessLogAction;
import com.adn.dabaeum.accesslog.domain.AdminAccessLogPage;
import com.adn.dabaeum.common.api.ApiMeta;
import com.adn.dabaeum.common.api.PageMeta;
import com.adn.dabaeum.common.web.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 접속기록 조회 (설계서 §10.2). 인가는 {@code ApiAuthorizationRules} 가 PLATFORM_ADMIN 으로 막는다.
 * 관리자 활동 기록이지 정보주체의 개인정보가 아니라 이 조회 자체에는 접속기록을 남기지 않는다.
 */
@RestController
@Profile({"local", "dev"})
@RequestMapping("/api/v1/admin/access-logs")
public class AdminAccessLogController {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final AdminAccessLogQueryService service;
    private final Clock clock;

    public AdminAccessLogController(AdminAccessLogQueryService service, Clock clock) {
        this.service = service;
        this.clock = clock;
    }

    @GetMapping
    public AdminAccessLogPageResponse list(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(required = false) UUID actorId,
        @RequestParam(required = false) AccessLogAction action,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
        HttpServletRequest servletRequest
    ) {
        AdminAccessLogPage result = service.list(page, size, actorId, action, from, to);
        int totalPages = (int) ((result.totalElements() + size - 1) / size);
        return new AdminAccessLogPageResponse(
            result.entries().stream().map(AdminAccessLogResponse::from).toList(),
            new PageMeta(page, size, result.totalElements(), totalPages),
            new ApiMeta(
                (String) servletRequest.getAttribute(RequestIdFilter.ATTRIBUTE_NAME),
                OffsetDateTime.ofInstant(clock.instant(), SEOUL)));
    }
}
