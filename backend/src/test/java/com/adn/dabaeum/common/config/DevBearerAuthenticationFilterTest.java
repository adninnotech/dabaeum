package com.adn.dabaeum.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.adn.dabaeum.common.security.AuthenticatedUserPrincipal;
import jakarta.servlet.FilterChain;
import java.time.Clock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import tools.jackson.databind.ObjectMapper;

class DevBearerAuthenticationFilterTest {

    private static final String EXPECTED_TOKEN = "unit-test-token";

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void doesNotAuthenticateWithoutAuthorizationHeader() throws Exception {
        invoke(new MockHttpServletRequest());

        assertThat(SecurityContextHolder.getContext().getAuthentication())
            .isNull();
    }

    @Test
    void doesNotAuthenticateNonBearerScheme() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Basic " + EXPECTED_TOKEN);

        invoke(request);

        assertThat(SecurityContextHolder.getContext().getAuthentication())
            .isNull();
    }

    @Test
    void doesNotAuthenticateMismatchedToken() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer another-token");

        invoke(request);

        assertThat(SecurityContextHolder.getContext().getAuthentication())
            .isNull();
    }

    @Test
    void doesNotAuthenticateWhenConfiguredTokenIsEmpty() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer any-token");

        new DevBearerAuthenticationFilter(new DevBearerTokenProperties(""))
            .doFilter(request, new MockHttpServletResponse(), noopChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication())
            .isNull();
    }

    @Test
    void authenticatesMatchingTokenWithPlatformAdminAuthority() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + EXPECTED_TOKEN);

        invoke(request);

        Authentication authentication = SecurityContextHolder.getContext()
            .getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(authentication.getPrincipal())
            .isInstanceOfSatisfying(AuthenticatedUserPrincipal.class, principal -> {
                assertThat(principal.userId().toString())
                    .isEqualTo("ffffffff-0000-0000-0000-000000000001");
                assertThat(principal.provider()).isEqualTo("LOCAL");
                assertThat(principal.roles()).containsExactly("PLATFORM_ADMIN");
            });
        assertThat(authentication.getCredentials()).isNull();
        assertThat(authentication.getAuthorities())
            .extracting(Object::toString)
            .containsExactly("ROLE_PLATFORM_ADMIN");
    }

    @Test
    void createsDevelopmentFilterOnlyForLocalProfile() {
        new ApplicationContextRunner()
            .withUserConfiguration(DevBearerSecurityConfiguration.class)
            .withBean(Clock.class, Clock::systemUTC)
            .withBean(ObjectMapper.class, ObjectMapper::new)
            .withPropertyValues(
                "spring.profiles.active=test",
                "dabaeum.security.dev.bearer-token=" + EXPECTED_TOKEN
            )
            .run(context -> assertThat(
                context.getBeansOfType(DevBearerAuthenticationFilter.class)
            ).isEmpty());

        new ApplicationContextRunner()
            .withUserConfiguration(DevBearerSecurityConfiguration.class)
            .withBean(Clock.class, Clock::systemUTC)
            .withBean(ObjectMapper.class, ObjectMapper::new)
            .withPropertyValues(
                "spring.profiles.active=local",
                "dabaeum.security.dev.bearer-token=" + EXPECTED_TOKEN
            )
            .run(context -> assertThat(
                context.getBeansOfType(DevBearerAuthenticationFilter.class)
            ).hasSize(1));
    }

    private void invoke(MockHttpServletRequest request) throws Exception {
        new DevBearerAuthenticationFilter(
            new DevBearerTokenProperties(EXPECTED_TOKEN)
        ).doFilter(
            request,
            new MockHttpServletResponse(),
            noopChain()
        );
    }

    private static FilterChain noopChain() {
        return (request, response) -> {
        };
    }
}
