package com.adn.dabaeum.common.config;

import com.adn.dabaeum.authentication.application.AuthenticationPort;
import com.adn.dabaeum.common.api.ApiException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public final class ProductionJwtAuthenticationFilter
    extends OncePerRequestFilter {

    private static final String AUTHORIZATION = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final AuthenticationPort authenticationPort;
    private final SecurityErrorWriter securityErrorWriter;

    public ProductionJwtAuthenticationFilter(
        AuthenticationPort authenticationPort,
        SecurityErrorWriter securityErrorWriter
    ) {
        this.authenticationPort = authenticationPort;
        this.securityErrorWriter = securityErrorWriter;
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        String authorization = request.getHeader(AUTHORIZATION);
        if (authorization == null
            || !authorization.regionMatches(
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
