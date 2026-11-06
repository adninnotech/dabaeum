package com.adn.dabaeum.authentication.api;

import com.adn.dabaeum.authentication.application.ChangePasswordCommand;
import com.adn.dabaeum.authentication.application.LocalAccountApplicationService;
import com.adn.dabaeum.authentication.application.LocalAuthResult;
import com.adn.dabaeum.authentication.application.LoginCommand;
import com.adn.dabaeum.authentication.application.SignupCommand;
import com.adn.dabaeum.common.api.ApiMeta;
import com.adn.dabaeum.common.api.ApiResponse;
import com.adn.dabaeum.common.security.CurrentUserProvider;
import com.adn.dabaeum.common.web.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@Profile({"local", "dev"})
public class LocalAccountController {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final LocalAccountApplicationService service;
    private final CurrentUserProvider currentUserProvider;
    private final Clock clock;

    public LocalAccountController(
        LocalAccountApplicationService service,
        CurrentUserProvider currentUserProvider,
        Clock clock
    ) {
        this.service = service;
        this.currentUserProvider = currentUserProvider;
        this.clock = clock;
    }

    @PostMapping("/api/v1/auth/signup")
    public ResponseEntity<ApiResponse<AuthTokenResponse>> signup(
        @Valid @RequestBody SignupRequest request,
        HttpServletRequest servletRequest
    ) {
        LocalAuthResult result = service.signup(new SignupCommand(
            request.email(),
            request.password(),
            request.name(),
            request.phone(),
            request.birthDate(),
            request.agreedToUsage(),
            request.agreedToPrivacy(),
            request.agreedToMarketing()
        ));
        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
            .path("/api/v1/users/{userId}")
            .buildAndExpand(result.userId())
            .toUri();
        return ResponseEntity.created(location)
            .body(response(result, servletRequest));
    }

    @PostMapping("/api/v1/auth/login")
    public ApiResponse<AuthTokenResponse> login(
        @Valid @RequestBody LoginRequest request,
        HttpServletRequest servletRequest
    ) {
        return response(
            service.login(new LoginCommand(request.email(), request.password())),
            servletRequest
        );
    }

    @PutMapping("/api/v1/users/me/password")
    public ApiResponse<PasswordResetResultResponse> changePassword(
        @Valid @RequestBody ChangePasswordRequest request,
        HttpServletRequest servletRequest
    ) {
        UUID currentUserId = currentUserProvider.requireUserId();
        service.changePassword(new ChangePasswordCommand(
            currentUserId, request.currentPassword(), request.newPassword()));
        return new ApiResponse<>(
            new PasswordResetResultResponse(true),
            meta(requestId(servletRequest)));
    }

    private ApiResponse<AuthTokenResponse> response(
        LocalAuthResult result,
        HttpServletRequest request
    ) {
        return AuthTokenResponse.wrap(result, request, clock);
    }

    private ApiMeta meta(String requestId) {
        return new ApiMeta(requestId, OffsetDateTime.ofInstant(clock.instant(), SEOUL));
    }

    private String requestId(HttpServletRequest request) {
        return (String) request.getAttribute(RequestIdFilter.ATTRIBUTE_NAME);
    }
}
