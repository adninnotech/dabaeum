package com.adn.dabaeum.authentication.api;

import com.adn.dabaeum.authentication.application.LocalAuthResult;
import com.adn.dabaeum.common.api.ApiMeta;
import com.adn.dabaeum.common.api.ApiResponse;
import com.adn.dabaeum.common.web.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;

public record AuthTokenResponse(
    String accessToken,
    String tokenType,
    long expiresIn,
    SessionResponse session
) {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    static ApiResponse<AuthTokenResponse> wrap(
        LocalAuthResult result,
        HttpServletRequest request,
        Clock clock
    ) {
        SessionResponse session = new SessionResponse(
            result.userId(),
            result.provider(),
            result.roles().stream()
                .map(role -> new SessionResponse.SessionRole(
                    role.role(),
                    role.institutionId()
                ))
                .toList(),
            result.expiresAt()
        );
        return new ApiResponse<>(
            new AuthTokenResponse(
                result.accessToken(),
                result.tokenType(),
                result.expiresIn(),
                session
            ),
            meta(request, clock)
        );
    }

    static ApiMeta meta(HttpServletRequest request, Clock clock) {
        String requestId = (String) request.getAttribute(
            RequestIdFilter.ATTRIBUTE_NAME
        );
        return new ApiMeta(requestId, OffsetDateTime.ofInstant(clock.instant(), SEOUL));
    }
}
