package com.adn.dabaeum.authentication.api;

import com.adn.dabaeum.authentication.application.LocalAccountApplicationService;
import com.adn.dabaeum.authentication.application.LocalAuthResult;
import com.adn.dabaeum.authentication.application.LoginCommand;
import com.adn.dabaeum.authentication.application.SignupCommand;
import com.adn.dabaeum.common.api.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.Clock;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@Profile({"local", "dev"})
@RequestMapping("/api/v1/auth")
public class LocalAccountController {

    private final LocalAccountApplicationService service;
    private final Clock clock;

    public LocalAccountController(
        LocalAccountApplicationService service,
        Clock clock
    ) {
        this.service = service;
        this.clock = clock;
    }

    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<AuthTokenResponse>> signup(
        @Valid @RequestBody SignupRequest request,
        HttpServletRequest servletRequest
    ) {
        LocalAuthResult result = service.signup(new SignupCommand(
            request.email(),
            request.password(),
            request.name(),
            request.phone(),
            request.birthDate()
        ));
        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
            .path("/api/v1/users/{userId}")
            .buildAndExpand(result.userId())
            .toUri();
        return ResponseEntity.created(location)
            .body(response(result, servletRequest));
    }

    @PostMapping("/login")
    public ApiResponse<AuthTokenResponse> login(
        @Valid @RequestBody LoginRequest request,
        HttpServletRequest servletRequest
    ) {
        return response(
            service.login(new LoginCommand(request.email(), request.password())),
            servletRequest
        );
    }

    private ApiResponse<AuthTokenResponse> response(
        LocalAuthResult result,
        HttpServletRequest request
    ) {
        return AuthTokenResponse.wrap(result, request, clock);
    }
}
