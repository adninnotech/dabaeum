package com.adn.dabaeum.authentication.api;

import com.adn.dabaeum.authentication.application.PasswordExpiryPolicy;
import com.adn.dabaeum.common.api.ApiMeta;
import com.adn.dabaeum.common.api.ApiResponse;
import com.adn.dabaeum.common.security.AuthenticatedRole;
import com.adn.dabaeum.common.security.AuthenticatedTokenDetails;
import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import com.adn.dabaeum.common.security.CurrentUserProvider;
import com.adn.dabaeum.common.web.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class SessionController {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final CurrentUserProvider currentUserProvider;
    private final Clock clock;
    // 판정기는 LOCAL 저장소가 있는 local/dev 에만 있다. 없으면 항상 false.
    private final ObjectProvider<PasswordExpiryPolicy> passwordExpiry;

    public SessionController(
        CurrentUserProvider currentUserProvider,
        Clock clock,
        ObjectProvider<PasswordExpiryPolicy> passwordExpiry
    ) {
        this.currentUserProvider = currentUserProvider;
        this.clock = clock;
        this.passwordExpiry = passwordExpiry;
    }

    @GetMapping("/session")
    public ApiResponse<SessionResponse> session(
        HttpServletRequest servletRequest
    ) {
        AuthenticatedUserContext context = currentUserProvider.requireContext();
        Authentication authentication = SecurityContextHolder
            .getContext()
            .getAuthentication();
        InstantAndContext details = tokenDetails(authentication, context);
        List<SessionResponse.SessionRole> roles = details.context().roles()
            .stream()
            .sorted(Comparator.comparing(AuthenticatedRole::role)
                .thenComparing(role -> String.valueOf(role.institutionId())))
            .map(role -> new SessionResponse.SessionRole(
                role.role(),
                role.institutionId()
            ))
            .toList();
        SessionResponse response = new SessionResponse(
            details.context().userId(),
            details.context().provider(),
            roles,
            details.expiresAt(),
            passwordExpired(details.context())
        );
        String requestId = (String) servletRequest.getAttribute(
            RequestIdFilter.ATTRIBUTE_NAME
        );
        return new ApiResponse<>(
            response,
            new ApiMeta(
                requestId,
                OffsetDateTime.ofInstant(clock.instant(), SEOUL)
            )
        );
    }

    private boolean passwordExpired(AuthenticatedUserContext context) {
        PasswordExpiryPolicy policy = passwordExpiry.getIfAvailable();
        return policy != null && policy.isExpired(
            context.userId(),
            context.roles().stream().map(AuthenticatedRole::role).toList());
    }

    private InstantAndContext tokenDetails(
        Authentication authentication,
        AuthenticatedUserContext fallback
    ) {
        if (authentication != null
            && authentication.getDetails() instanceof AuthenticatedTokenDetails details) {
            return new InstantAndContext(
                details.expiresAt(),
                details.context() == null ? fallback : details.context()
            );
        }
        return new InstantAndContext(null, fallback);
    }

    private record InstantAndContext(
        java.time.Instant expiresAt,
        AuthenticatedUserContext context
    ) {
    }
}
