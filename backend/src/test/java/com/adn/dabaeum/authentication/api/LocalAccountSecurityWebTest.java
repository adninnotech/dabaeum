package com.adn.dabaeum.authentication.api;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.adn.dabaeum.authentication.application.AuthenticationPort;
import com.adn.dabaeum.common.api.ApiExceptionHandler;
import com.adn.dabaeum.common.config.ClockConfiguration;
import com.adn.dabaeum.common.config.DevBearerSecurityConfiguration;
import com.adn.dabaeum.common.config.JacksonConfiguration;
import com.adn.dabaeum.common.config.LocalAccountAuthenticationFilter;
import com.adn.dabaeum.common.config.SecurityConfiguration;
import com.adn.dabaeum.common.config.SecurityErrorWriter;
import com.adn.dabaeum.common.security.AuthenticatedRole;
import com.adn.dabaeum.common.security.AuthenticatedTokenDetails;
import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import com.adn.dabaeum.common.security.AuthenticatedUserPrincipal;
import com.adn.dabaeum.common.web.RequestIdFilter;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SessionController.class)
@ActiveProfiles("local")
@TestPropertySource(properties =
    "dabaeum.security.dev.bearer-token=local-security-web-dev-token")
@Import({
    ApiExceptionHandler.class,
    ClockConfiguration.class,
    SecurityConfiguration.class,
    DevBearerSecurityConfiguration.class,
    JacksonConfiguration.class,
    RequestIdFilter.class,
    LocalAccountSecurityWebTest.FilterConfiguration.class
})
class LocalAccountSecurityWebTest {

    private static final UUID USER_ID = UUID.fromString(
        "11111111-1111-1111-1111-111111111111"
    );
    private static final Instant EXPIRES_AT =
        Instant.parse("2026-08-12T01:00:00Z");

    @Autowired MockMvc mockMvc;

    @Test
    void localLearnerTokenAuthenticatesSessionButCannotUseAdminApi()
        throws Exception {
        mockMvc.perform(get("/api/v1/auth/session")
                .header("Authorization", "Bearer local-learner-token"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.userId").value(USER_ID.toString()))
            .andExpect(jsonPath("$.data.roles[0].role").value("LEARNER"));

        mockMvc.perform(post("/api/v1/users")
                .header("Authorization", "Bearer local-learner-token"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FilterConfiguration {

        @Bean
        AuthenticationPort localTestAuthenticationPort() {
            return token -> {
                if (!"local-learner-token".equals(token)) {
                    throw new IllegalArgumentException("unexpected token");
                }
                AuthenticatedUserContext context = new AuthenticatedUserContext(
                    USER_ID,
                    "LOCAL",
                    Set.of(new AuthenticatedRole("LEARNER", null))
                );
                AuthenticatedUserPrincipal principal = new AuthenticatedUserPrincipal(
                    USER_ID,
                    "LOCAL",
                    Set.of("LEARNER")
                );
                UsernamePasswordAuthenticationToken authentication =
                    UsernamePasswordAuthenticationToken.authenticated(
                        principal,
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_LEARNER"))
                    );
                authentication.setDetails(new AuthenticatedTokenDetails(
                    EXPIRES_AT,
                    context
                ));
                return authentication;
            };
        }

        @Bean
        LocalAccountAuthenticationFilter localAccountAuthenticationFilter(
            AuthenticationPort localTestAuthenticationPort,
            SecurityErrorWriter securityErrorWriter
        ) {
            return new LocalAccountAuthenticationFilter(
                localTestAuthenticationPort,
                securityErrorWriter
            );
        }
    }
}
