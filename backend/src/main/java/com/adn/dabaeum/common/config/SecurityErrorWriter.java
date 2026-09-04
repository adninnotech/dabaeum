package com.adn.dabaeum.common.config;

import com.adn.dabaeum.common.api.ApiErrorCode;
import com.adn.dabaeum.common.api.ApiErrorResponse;
import com.adn.dabaeum.common.api.ApiException;
import com.adn.dabaeum.common.web.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import tools.jackson.databind.ObjectMapper;

public final class SecurityErrorWriter
    implements AuthenticationEntryPoint, AccessDeniedHandler {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final ObjectMapper objectMapper;
    private final Clock clock;

    public SecurityErrorWriter(ObjectMapper objectMapper, Clock clock) {
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    public void commence(
        HttpServletRequest request,
        HttpServletResponse response,
        AuthenticationException exception
    ) throws IOException {
        write(
            request,
            response,
            HttpServletResponse.SC_UNAUTHORIZED,
            ApiErrorCode.UNAUTHORIZED,
            "Authentication is required"
        );
    }

    @Override
    public void handle(
        HttpServletRequest request,
        HttpServletResponse response,
        AccessDeniedException exception
    ) throws IOException {
        write(
            request,
            response,
            HttpServletResponse.SC_FORBIDDEN,
            ApiErrorCode.FORBIDDEN,
            "Access is forbidden"
        );
    }

    public void writeAuthenticationFailure(
        HttpServletRequest request,
        HttpServletResponse response,
        ApiException exception
    ) throws IOException {
        write(
            request,
            response,
            exception.status().value(),
            exception.code(),
            exception.getMessage()
        );
    }

    private void write(
        HttpServletRequest request,
        HttpServletResponse response,
        int status,
        ApiErrorCode code,
        String message
    ) throws IOException {
        if (response.isCommitted()) {
            return;
        }

        String requestId = requestId(request);
        response.setStatus(status);
        response.setCharacterEncoding("UTF-8");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader(RequestIdFilter.HEADER_NAME, requestId);

        objectMapper.writeValue(
            response.getWriter(),
            new ApiErrorResponse(
                code.name(),
                message,
                List.of(),
                requestId,
                OffsetDateTime.ofInstant(clock.instant(), SEOUL)
            )
        );
    }

    private String requestId(HttpServletRequest request) {
        Object attribute = request.getAttribute(RequestIdFilter.ATTRIBUTE_NAME);
        if (attribute instanceof String value && !value.isBlank()) {
            return value;
        }

        String requestId = UUID.randomUUID().toString();
        request.setAttribute(RequestIdFilter.ATTRIBUTE_NAME, requestId);
        return requestId;
    }
}
