package com.adn.dabaeum.common.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.adn.dabaeum.authentication.application.AuthenticationPort;
import com.adn.dabaeum.common.api.ApiErrorCode;
import com.adn.dabaeum.common.api.ApiException;
import jakarta.servlet.FilterChain;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class LocalAccountAuthenticationFilterTest {

    private static final String TOKEN = "header.payload.signature";

    @Mock AuthenticationPort authenticationPort;
    @Mock FilterChain filterChain;

    private LocalAccountAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(
            Instant.parse("2026-08-12T00:00:00Z"),
            ZoneOffset.UTC
        );
        filter = new LocalAccountAuthenticationFilter(
            authenticationPort,
            new SecurityErrorWriter(new ObjectMapper(), clock)
        );
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void authenticatesBearerAndContinuesChain() throws Exception {
        TestingAuthenticationToken authentication = new TestingAuthenticationToken(
            "learner",
            null,
            "ROLE_LEARNER"
        );
        authentication.setAuthenticated(true);
        when(authenticationPort.authenticate(TOKEN)).thenReturn(authentication);
        MockHttpServletRequest request = request("/api/v1/users/me");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication())
            .isSameAs(authentication);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void skipsPublicAccountPathsAndExistingAuthentication() throws Exception {
        MockHttpServletRequest signup = request("/api/v1/auth/signup");
        filter.doFilter(signup, new MockHttpServletResponse(), filterChain);
        verify(authenticationPort, never()).authenticate(TOKEN);

        SecurityContextHolder.getContext().setAuthentication(
            new TestingAuthenticationToken("dev-admin", null, "ROLE_PLATFORM_ADMIN")
        );
        MockHttpServletRequest protectedRequest = request("/api/v1/users/me");
        filter.doFilter(
            protectedRequest,
            new MockHttpServletResponse(),
            filterChain
        );
        verify(authenticationPort, never()).authenticate(TOKEN);
    }

    @Test
    void writesCommonUnauthorizedResponseForInvalidJwt() throws Exception {
        when(authenticationPort.authenticate(TOKEN)).thenThrow(new ApiException(
            HttpStatus.UNAUTHORIZED,
            ApiErrorCode.AUTHENTICATION_FAILED,
            "Authentication failed"
        ));
        MockHttpServletRequest request = request("/api/v1/users/me");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString())
            .contains("AUTHENTICATION_FAILED")
            .doesNotContain(TOKEN);
        verify(filterChain, never()).doFilter(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any()
        );
    }

    private MockHttpServletRequest request(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.addHeader("Authorization", "Bearer " + TOKEN);
        return request;
    }
}
