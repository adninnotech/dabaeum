package com.adn.dabaeum.authentication.api;

import com.adn.dabaeum.authentication.application.AccountRecoveryService;
import com.adn.dabaeum.common.api.ApiMeta;
import com.adn.dabaeum.common.api.ApiResponse;
import com.adn.dabaeum.common.security.CurrentUserProvider;
import com.adn.dabaeum.common.web.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile({"local", "dev"})
@RequestMapping("/api/v1")
public class AccountRecoveryController {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final AccountRecoveryService service;
    private final CurrentUserProvider currentUserProvider;
    private final Clock clock;

    public AccountRecoveryController(
        AccountRecoveryService service,
        CurrentUserProvider currentUserProvider,
        Clock clock
    ) {
        this.service = service;
        this.currentUserProvider = currentUserProvider;
        this.clock = clock;
    }

    @GetMapping("/auth/email/availability")
    public ApiResponse<EmailAvailabilityResponse> checkEmailAvailability(
        @RequestParam String email,
        HttpServletRequest servletRequest
    ) {
        boolean available = service.isEmailAvailable(email);
        return new ApiResponse<>(
            new EmailAvailabilityResponse(available), meta(requestId(servletRequest)));
    }

    @PostMapping("/auth/password/reset-request")
    public ResponseEntity<ApiResponse<PasswordResetRequestedResponse>> requestReset(
        @RequestBody(required = false) PasswordResetRequestRequest request,
        HttpServletRequest servletRequest
    ) {
        service.requestPasswordReset(request == null ? null : request.email());
        return ResponseEntity.status(HttpStatus.ACCEPTED)
            .body(new ApiResponse<>(
                new PasswordResetRequestedResponse(true),
                meta(requestId(servletRequest))));
    }

    @PostMapping("/auth/password/reset")
    public ApiResponse<PasswordResetResultResponse> confirmReset(
        @RequestBody(required = false) PasswordResetConfirmRequest request,
        HttpServletRequest servletRequest
    ) {
        service.confirmPasswordReset(
            request == null ? null : request.token(),
            request == null ? null : request.password());
        return new ApiResponse<>(
            new PasswordResetResultResponse(true),
            meta(requestId(servletRequest)));
    }

    @PostMapping("/users/{userId}/password/reset")
    public ApiResponse<AdminPasswordResetResponse> adminReset(
        @PathVariable UUID userId,
        @RequestBody(required = false) AdminPasswordResetRequest request,
        HttpServletRequest servletRequest
    ) {
        AccountRecoveryService.AdminResetResult result = service.adminResetPassword(
            currentUserProvider.requireContext(), userId,
            request == null ? null : request.temporaryPassword());
        return new ApiResponse<>(
            new AdminPasswordResetResponse(result.resetAt(), result.temporaryPassword()),
            meta(requestId(servletRequest)));
    }

    private ApiMeta meta(String requestId) {
        return new ApiMeta(requestId, OffsetDateTime.ofInstant(clock.instant(), SEOUL));
    }

    private String requestId(HttpServletRequest request) {
        return (String) request.getAttribute(RequestIdFilter.ATTRIBUTE_NAME);
    }
}
