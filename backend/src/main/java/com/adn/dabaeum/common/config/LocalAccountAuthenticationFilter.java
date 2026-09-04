package com.adn.dabaeum.common.config;

import com.adn.dabaeum.authentication.application.AuthenticationPort;
import com.adn.dabaeum.common.api.ApiException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public final class LocalAccountAuthenticationFilter
    extends OncePerRequestFilter {

    private static final String AUTHORIZATION = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final Set<String> PUBLIC_PATHS = Set.of(
        "/api/v1/auth/signup",
        "/api/v1/auth/login"
    );

    private final AuthenticationPort authenticationPort;
    private final SecurityErrorWriter securityErrorWriter;

    public LocalAccountAuthenticationFilter(
        AuthenticationPort authenticationPort,
        SecurityErrorWriter securityErrorWriter
    ) {
        this.authenticationPort = authenticationPort;
        this.securityErrorWriter = securityErrorWriter;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return PUBLIC_PATHS.contains(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        Authentication current = SecurityContextHolder.getContext()
            .getAuthentication();
        if (current != null && current.isAuthenticated()) {
            filterChain.doFilter(request, response);
            return;
        }
        String authorization = request.getHeader(AUTHORIZATION);
        if (authorization == null || !authorization.regionMatches(
            true,
            0,
            BEARER_PREFIX,
            0,
            BEARER_PREFIX.length()
        )) {
            filterChain.doFilter(request, response);
            return;
        }
        String token = authorization.substring(BEARER_PREFIX.length()).trim();
        try {
            Authentication authentication = authenticationPort.authenticate(token);
            SecurityContextHolder.getContext().setAuthentication(authentication);
            filterChain.doFilter(request, response);
        } catch (ApiException exception) {
            SecurityContextHolder.clearContext();
            securityErrorWriter.writeAuthenticationFailure(
                request,
                response,
                exception
            );
        }
    }
}
