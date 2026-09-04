package com.adn.dabaeum.authentication.api;

import com.adn.dabaeum.authentication.application.DadaeguLoginService;
import com.adn.dabaeum.authentication.application.LinkLocalAccountCommand;
import com.adn.dabaeum.authentication.application.LocalAccountApplicationService;
import com.adn.dabaeum.authentication.domain.LocalAccountCredential;
import com.adn.dabaeum.common.api.ApiResponse;
import com.adn.dabaeum.common.security.CurrentUserProvider;
import com.adn.dabaeum.identity.api.IdentityResponse;
import com.adn.dabaeum.identity.domain.IdentityProvider;
import com.adn.dabaeum.identity.domain.UserIdentity;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.Clock;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 통합 회원: 로그인된 사용자에게 두 번째 로그인 수단을 붙인다.
 * 일반회원 → 다대구(QR), 다대구 → 일반(email+password). Role 은 건드리지 않는다.
 */
@RestController
@Profile({"local", "dev"})
@RequestMapping("/api/v1/users/me/identities")
public class AccountLinkController {

    private final DadaeguLoginService dadaeguLoginService;
    private final LocalAccountApplicationService localAccountService;
    private final CurrentUserProvider currentUserProvider;
    private final Clock clock;

    public AccountLinkController(
        DadaeguLoginService dadaeguLoginService,
        LocalAccountApplicationService localAccountService,
        CurrentUserProvider currentUserProvider,
        Clock clock
    ) {
        this.dadaeguLoginService = dadaeguLoginService;
        this.localAccountService = localAccountService;
        this.currentUserProvider = currentUserProvider;
        this.clock = clock;
    }

    @PostMapping("/dadaegu")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<IdentityResponse> linkDadaegu(
        @Valid @RequestBody DadaeguLoginRequest request,
        HttpServletRequest servletRequest
    ) {
        UserIdentity identity = dadaeguLoginService.link(
            currentUserProvider.requireUserId(),
            request.did(),
            request.name(),
            request.birthdate(),
            request.phoneNumber()
        );
        return new ApiResponse<>(
            new IdentityResponse(
                identity.id(), identity.provider(), identity.verifiedAt(),
                identity.createdAt(), identity.updatedAt()
            ),
            AuthTokenResponse.meta(servletRequest, clock)
        );
    }

    @PostMapping("/local")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<IdentityResponse> linkLocal(
        @Valid @RequestBody LocalIdentityLinkRequest request,
        HttpServletRequest servletRequest
    ) {
        LocalAccountCredential credential = localAccountService.linkLocal(
            new LinkLocalAccountCommand(
                currentUserProvider.requireUserId(), request.email(), request.password()
            )
        );
        // LOCAL 은 저장 시 verified_at = created_at (LocalAccountMapper.xml insert)
        return new ApiResponse<>(
            new IdentityResponse(
                credential.identityId(), IdentityProvider.LOCAL, credential.createdAt(),
                credential.createdAt(), credential.updatedAt()
            ),
            AuthTokenResponse.meta(servletRequest, clock)
        );
    }
}
