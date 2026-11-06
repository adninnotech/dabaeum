package com.adn.dabaeum.administrator.api;

import com.adn.dabaeum.accesslog.domain.AccessLogAction;
import com.adn.dabaeum.accesslog.web.PersonalDataAccess;
import com.adn.dabaeum.administrator.application.AdministratorService;
import com.adn.dabaeum.administrator.application.CreatedAdministrator;
import com.adn.dabaeum.administrator.domain.AdministratorPage;
import com.adn.dabaeum.common.api.ApiMeta;
import com.adn.dabaeum.common.api.ApiResponse;
import com.adn.dabaeum.common.api.PageMeta;
import com.adn.dabaeum.common.web.RequestIdFilter;
import com.adn.dabaeum.role.domain.UserRole;
import com.adn.dabaeum.user.domain.UserStatus;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/** 관리자 목록·생성 (설계서 §10.1). 인가는 {@code ApiAuthorizationRules} 가 PLATFORM_ADMIN 으로 막는다. */
@RestController
@Profile({"local", "dev"})
@RequestMapping("/api/v1/admin/administrators")
public class AdministratorController {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final AdministratorService service;
    private final Clock clock;

    public AdministratorController(AdministratorService service, Clock clock) {
        this.service = service;
        this.clock = clock;
    }

    @GetMapping
    @PersonalDataAccess(action = AccessLogAction.READ, targetField = "userId")
    public AdministratorPageResponse list(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(required = false) UserRole role,
        @RequestParam(required = false) UserStatus status,
        @RequestParam(required = false) String q,
        HttpServletRequest servletRequest
    ) {
        AdministratorPage result = service.list(page, size, role, status, q);
        int totalPages = (int) ((result.totalElements() + size - 1) / size);
        return new AdministratorPageResponse(
            result.administrators().stream().map(AdministratorResponse::from).toList(),
            new PageMeta(page, size, result.totalElements(), totalPages),
            meta(servletRequest));
    }

    @PostMapping
    @PersonalDataAccess(action = AccessLogAction.UPDATE, targetField = "userId")
    public ResponseEntity<ApiResponse<AdministratorCreatedData>> create(
        @Valid @RequestBody AdministratorCreateRequest request,
        HttpServletRequest servletRequest
    ) {
        CreatedAdministrator created = service.create(request.toCommand());
        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
            .path("/api/v1/users/{userId}")
            .buildAndExpand(created.administrator().userId())
            .toUri();
        return ResponseEntity.created(location).body(new ApiResponse<>(
            new AdministratorCreatedData(
                created.administrator().userId(),
                AdministratorResponse.from(created.administrator()),
                created.temporaryPassword()),
            meta(servletRequest)));
    }

    private ApiMeta meta(HttpServletRequest request) {
        return new ApiMeta(
            (String) request.getAttribute(RequestIdFilter.ATTRIBUTE_NAME),
            OffsetDateTime.ofInstant(clock.instant(), SEOUL));
    }
}
