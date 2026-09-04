package com.adn.dabaeum.authentication.api;

import com.adn.dabaeum.authentication.application.DadaeguLoginProperties;
import com.adn.dabaeum.authentication.application.DadaeguLoginService;
import com.adn.dabaeum.common.api.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.Clock;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
    public ApiResponse<AuthTokenResponse> login(
        @Valid @RequestBody DadaeguLoginRequest request,
        HttpServletRequest servletRequest
    ) {
        return AuthTokenResponse.wrap(
            service.login(
                request.did(),
                request.name(),
                request.birthdate(),
                request.phoneNumber()
            ),
            servletRequest,
            clock
        );
    }
}
