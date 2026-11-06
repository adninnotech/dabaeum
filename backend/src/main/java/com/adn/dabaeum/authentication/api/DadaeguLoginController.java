package com.adn.dabaeum.authentication.api;

import com.adn.dabaeum.authentication.application.DadaeguLoginProperties;
import com.adn.dabaeum.authentication.application.DadaeguLoginOutcome;
import com.adn.dabaeum.authentication.application.DadaeguLoginService;
import com.adn.dabaeum.authentication.application.DadaeguSignupCommand;
import com.adn.dabaeum.authentication.application.LocalAuthResult;
import com.adn.dabaeum.common.api.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.Clock;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@Profile({"local", "dev"})
@RequestMapping("/api/v1/auth/dadaegu")
public class DadaeguLoginController {

    private final DadaeguLoginService service;
    private final DadaeguLoginProperties properties;
    private final Clock clock;

    public DadaeguLoginController(
        DadaeguLoginService service,
        DadaeguLoginProperties properties,
        Clock clock
    ) {
        this.service = service;
        this.properties = properties;
        this.clock = clock;
    }

    /** 프론트가 DIDLogin.loginPersonal 에 넣을 siteId. */
    @GetMapping("/config")
    public ApiResponse<DadaeguConfigResponse> config(HttpServletRequest request) {
        return new ApiResponse<>(
            new DadaeguConfigResponse(properties.siteId()),
            AuthTokenResponse.meta(request, clock)
        );
    }

    @PostMapping("/login")
    public ApiResponse<DadaeguLoginData> login(
        @Valid @RequestBody DadaeguLoginRequest request,
        HttpServletRequest servletRequest
    ) {
        DadaeguLoginOutcome outcome = service.login(
            request.did(), request.name(), request.birthdate(), request.phoneNumber());
        DadaeguLoginData data = switch (outcome) {
            case DadaeguLoginOutcome.LoggedIn loggedIn -> DadaeguLoginData.LoggedIn.of(
                AuthTokenResponse.wrap(loggedIn.result(), servletRequest, clock).data());
            case DadaeguLoginOutcome.RegistrationRequired required ->
                new DadaeguLoginData.RegistrationRequired(
                    required.registrationToken(), required.expiresAt(), required.maskedName());
        };
        return new ApiResponse<>(data, AuthTokenResponse.meta(servletRequest, clock));
    }

    /** 다대구 첫 로그인의 두 번째 단계: 가입 토큰 + 구·군 + 약관으로 계정을 만든다 (설계서 §4.2). */
    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<AuthTokenResponse>> signup(
        @Valid @RequestBody DadaeguSignupRequest request,
        HttpServletRequest servletRequest
    ) {
        LocalAuthResult result = service.signup(new DadaeguSignupCommand(
            request.registrationToken(), request.interestCodes(), request.agreedToInterest(),
            request.agreedToUsage(), request.agreedToPrivacy(), request.agreedToMarketing()));
        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
            .path("/api/v1/users/{userId}")
            .buildAndExpand(result.userId())
            .toUri();
        return ResponseEntity.created(location)
            .body(AuthTokenResponse.wrap(result, servletRequest, clock));
    }
}
