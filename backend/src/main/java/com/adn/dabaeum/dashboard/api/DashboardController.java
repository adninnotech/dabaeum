package com.adn.dabaeum.dashboard.api;

import com.adn.dabaeum.common.api.ApiMeta;
import com.adn.dabaeum.common.api.ApiResponse;
import com.adn.dabaeum.common.security.CurrentUserProvider;
import com.adn.dabaeum.common.web.RequestIdFilter;
import com.adn.dabaeum.dashboard.application.DashboardService;
import com.adn.dabaeum.dashboard.domain.InstitutionDashboard;
import com.adn.dabaeum.dashboard.domain.PlatformDashboard;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile({"local", "dev"})
@RequestMapping("/api/v1")
public class DashboardController {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final DashboardService service;
    private final CurrentUserProvider currentUserProvider;
    private final Clock clock;

    public DashboardController(
        DashboardService service,
        CurrentUserProvider currentUserProvider,
        Clock clock
    ) {
        this.service = service;
        this.currentUserProvider = currentUserProvider;
        this.clock = clock;
    }

    @GetMapping("/admin/dashboard")
    public ApiResponse<PlatformDashboard> platformDashboard(
        HttpServletRequest servletRequest
    ) {
        return new ApiResponse<>(
            service.platformDashboard(currentUserProvider.requireContext()),
            meta(requestId(servletRequest)));
    }

    @GetMapping("/institutions/{institutionId}/dashboard")
    public ApiResponse<InstitutionDashboard> institutionDashboard(
        @PathVariable UUID institutionId,
        HttpServletRequest servletRequest
    ) {
        return new ApiResponse<>(
            service.institutionDashboard(
                currentUserProvider.requireContext(), institutionId),
            meta(requestId(servletRequest)));
    }

    private ApiMeta meta(String requestId) {
        return new ApiMeta(requestId, OffsetDateTime.ofInstant(clock.instant(), SEOUL));
    }

    private String requestId(HttpServletRequest request) {
        return (String) request.getAttribute(RequestIdFilter.ATTRIBUTE_NAME);
    }
}
