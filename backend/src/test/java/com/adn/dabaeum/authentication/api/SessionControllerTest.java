package com.adn.dabaeum.authentication.api;

import static com.atlassian.oai.validator.mockmvc.OpenApiValidationMatchers.openApi;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.adn.dabaeum.common.api.ApiExceptionHandler;
import com.adn.dabaeum.common.config.ClockConfiguration;
import com.adn.dabaeum.common.config.DevBearerSecurityConfiguration;
import com.adn.dabaeum.common.config.JacksonConfiguration;
import com.adn.dabaeum.common.config.SecurityConfiguration;
import com.adn.dabaeum.common.security.AuthenticatedRole;
import com.adn.dabaeum.common.security.AuthenticatedTokenDetails;
import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import com.adn.dabaeum.common.security.AuthenticatedUserPrincipal;
import com.adn.dabaeum.common.web.RequestIdFilter;
import com.adn.dabaeum.testsupport.OpenApiContract;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(SessionController.class)
@ActiveProfiles("local")
@TestPropertySource(properties =
    "dabaeum.security.dev.bearer-token=stage3-session-test-token")
@Import({
    ApiExceptionHandler.class,
    ClockConfiguration.class,
    SecurityConfiguration.class,
    DevBearerSecurityConfiguration.class,
    JacksonConfiguration.class,
    RequestIdFilter.class
})
class SessionControllerTest {

    private static final Path CONTRACT = Path.of(
        "docs/api/dabaeum-api-v1.yaml"
    ).toAbsolutePath();
    private static final UUID USER_ID = UUID.fromString(
        "11111111-1111-1111-1111-111111111111"
    );
    private static final UUID INSTITUTION_ID = UUID.fromString(
        "22222222-2222-2222-2222-222222222222"
    );
    private static final Instant EXPIRES_AT =
        Instant.parse("2026-08-04T01:00:00Z");
    private static final String REQUEST_ID =
        "8b706c0e-96cf-4d0a-b357-d80752facf1c";

    @Autowired
    MockMvc mockMvc;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void returnsOnlyPublicAuthenticatedSessionData() throws Exception {
        mockMvc.perform(get("/api/v1/auth/session")
                .with(principal())
                .header(RequestIdFilter.HEADER_NAME, REQUEST_ID))
            .andExpect(status().isOk())
            .andExpect(header().string(
                RequestIdFilter.HEADER_NAME,
                REQUEST_ID
            ))
            .andExpect(jsonPath("$.data.userId").value(USER_ID.toString()))
            .andExpect(jsonPath("$.data.provider").value("DADAEGU"))
            .andExpect(jsonPath("$.data.roles[0].role")
                .value("INSTRUCTOR"))
            .andExpect(jsonPath("$.data.roles[0].institutionId")
                .value(INSTITUTION_ID.toString()))
            .andExpect(jsonPath("$.data.roles[1].role")
                .value("PLATFORM_ADMIN"))
            .andExpect(jsonPath("$.data.roles[1].institutionId")
                .doesNotExist())
            .andExpect(jsonPath("$.data.expiresAt")
                .value(EXPIRES_AT.toString()))
            .andExpect(jsonPath("$.data.token").doesNotExist())
            .andExpect(jsonPath("$.data.providerSubject").doesNotExist())
            .andExpect(jsonPath("$.data.metadata").doesNotExist())
            .andExpect(jsonPath("$.meta.requestId").value(REQUEST_ID))
            .andExpect(openApi().isValid(OpenApiContract.VALIDATOR));
    }

    @Test
    void requiresAuthenticationForSession() throws Exception {
        mockMvc.perform(get("/api/v1/auth/session"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void rejectsStringPrincipalWithoutConvertingItToUserId() throws Exception {
        mockMvc.perform(get("/api/v1/auth/session")
                .with(authentication(new UsernamePasswordAuthenticationToken(
                    "local-platform-admin",
                    null,
                    Set.of(new SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN"))
                ))))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    private RequestPostProcessor principal() {
        AuthenticatedUserContext context = new AuthenticatedUserContext(
            USER_ID,
            "DADAEGU",
            Set.of(
                new AuthenticatedRole("INSTRUCTOR", INSTITUTION_ID),
                new AuthenticatedRole("PLATFORM_ADMIN", null)
            )
        );
        AuthenticatedUserPrincipal principal = new AuthenticatedUserPrincipal(
            USER_ID,
            "DADAEGU",
            Set.of("INSTRUCTOR", "PLATFORM_ADMIN")
        );
        UsernamePasswordAuthenticationToken authentication =
            UsernamePasswordAuthenticationToken.authenticated(
                principal,
                null,
                Set.of(
                    new SimpleGrantedAuthority("ROLE_INSTRUCTOR"),
                    new SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN")
                )
            );
        authentication.setDetails(new AuthenticatedTokenDetails(
            EXPIRES_AT,
            context
        ));
        return authentication(authentication);
    }
}
