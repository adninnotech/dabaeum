package com.adn.dabaeum.authentication.api;

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

    public SessionController(
        CurrentUserProvider currentUserProvider,
        Clock clock
    ) {
        this.currentUserProvider = currentUserProvider;
        this.clock = clock;
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
            details.expiresAt()
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
