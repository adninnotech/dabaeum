package com.adn.dabaeum.authentication.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.adn.dabaeum.authentication.application.DadaeguLoginService;
import com.adn.dabaeum.authentication.application.LinkLocalAccountCommand;
import com.adn.dabaeum.authentication.application.LocalAccountApplicationService;
import com.adn.dabaeum.authentication.domain.LocalAccountCredential;
import com.adn.dabaeum.common.api.ApiErrorCode;
import com.adn.dabaeum.common.api.ApiException;
import com.adn.dabaeum.common.api.ApiExceptionHandler;
import com.adn.dabaeum.common.config.ClockConfiguration;
import com.adn.dabaeum.common.config.DevBearerSecurityConfiguration;
import com.adn.dabaeum.common.config.JacksonConfiguration;
import com.adn.dabaeum.common.config.SecurityConfiguration;
import com.adn.dabaeum.common.security.AuthenticatedUserPrincipal;
import com.adn.dabaeum.common.web.RequestIdFilter;
import com.adn.dabaeum.identity.domain.IdentityProvider;
import com.adn.dabaeum.identity.domain.UserIdentity;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(AccountLinkController.class)
@ActiveProfiles("local")
@TestPropertySource(properties =
    "dabaeum.security.dev.bearer-token=account-link-controller-test")
@Import({
    ApiExceptionHandler.class,
    ClockConfiguration.class,
    SecurityConfiguration.class,
    DevBearerSecurityConfiguration.class,
    JacksonConfiguration.class,
    RequestIdFilter.class
})
class AccountLinkControllerTest {

    private static final UUID USER_ID = UUID.fromString(
        "11111111-1111-1111-1111-111111111111"
    );
    private static final UUID IDENTITY_ID = UUID.fromString(
        "22222222-2222-2222-2222-222222222222"
    );
    private static final Instant NOW = Instant.parse("2026-09-03T00:00:00Z");
    private static final String REQUEST_ID = "33333333-3333-3333-3333-333333333333";

    @Autowired MockMvc mockMvc;
    @MockitoBean DadaeguLoginService dadaeguLoginService;
    @MockitoBean LocalAccountApplicationService localAccountService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void linksDadaeguIdentityForCurrentUser() throws Exception {
        when(dadaeguLoginService.link(
            USER_ID, "ZW5jLWRpZA==", "ZW5jLW5hbWU=", "ZW5jLWJpcnRo", "ZW5jLXBob25l"))
            .thenReturn(new UserIdentity(
                IDENTITY_ID, USER_ID, IdentityProvider.DADAEGU,
                "did:daegu:x", "did:daegu:x", NOW, null, NOW, NOW));

        mockMvc.perform(post("/api/v1/users/me/identities/dadaegu")
                .with(principal("LOCAL"))
                .header(RequestIdFilter.HEADER_NAME, REQUEST_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "did": "ZW5jLWRpZA==",
                      "name": "ZW5jLW5hbWU=",
                      "birthdate": "ZW5jLWJpcnRo",
                      "phoneNumber": "ZW5jLXBob25l",
                      "ci": "aWdub3JlZA=="
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.id").value(IDENTITY_ID.toString()))
            .andExpect(jsonPath("$.data.provider").value("DADAEGU"))
            .andExpect(jsonPath("$.data.verifiedAt").value(NOW.toString()))
            .andExpect(jsonPath("$.data.providerSubject").doesNotExist())
            .andExpect(jsonPath("$.data.externalDid").doesNotExist())
            .andExpect(jsonPath("$.meta.requestId").value(REQUEST_ID));
    }

    @Test
    void linksLocalCredentialForCurrentUser() throws Exception {
        when(localAccountService.linkLocal(
            new LinkLocalAccountCommand(USER_ID, "me@example.com", "learning-2026!")))
            .thenReturn(new LocalAccountCredential(
                IDENTITY_ID, USER_ID, "me@example.com", "$2a$04$hash", NOW, NOW));

        mockMvc.perform(post("/api/v1/users/me/identities/local")
                .with(principal("DADAEGU"))
                .header(RequestIdFilter.HEADER_NAME, REQUEST_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email": "me@example.com", "password": "learning-2026!"}
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.id").value(IDENTITY_ID.toString()))
            .andExpect(jsonPath("$.data.provider").value("LOCAL"))
            .andExpect(jsonPath("$.data.verifiedAt").value(NOW.toString()))
            .andExpect(jsonPath("$.data.passwordHash").doesNotExist())
            .andExpect(jsonPath("$.data.normalizedEmail").doesNotExist())
            .andExpect(jsonPath("$.meta.requestId").value(REQUEST_ID));
    }

    @Test
    void mapsIdentityConflictTo409() throws Exception {
        when(localAccountService.linkLocal(any())).thenThrow(new ApiException(
            HttpStatus.CONFLICT, ApiErrorCode.IDENTITY_CONFLICT, "Identity already exists"));

        mockMvc.perform(post("/api/v1/users/me/identities/local")
                .with(principal("DADAEGU"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email": "me@example.com", "password": "learning-2026!"}
                    """))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("IDENTITY_CONFLICT"));
    }

    @Test
    void rejectsBlankFieldsWith422() throws Exception {
        mockMvc.perform(post("/api/v1/users/me/identities/local")
                .with(principal("DADAEGU"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email": "", "password": ""}
                    """))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/users/me/identities/dadaegu")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"did": "ZW5jLWRpZA=="}
                    """))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mockMvc.perform(post("/api/v1/users/me/identities/local")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email": "me@example.com", "password": "learning-2026!"}
                    """))
            .andExpect(status().isUnauthorized());
    }

    private static RequestPostProcessor principal(String provider) {
        AuthenticatedUserPrincipal principal = new AuthenticatedUserPrincipal(
            USER_ID, provider, Set.of("LEARNER")
        );
        return authentication(UsernamePasswordAuthenticationToken.authenticated(
            principal, null, Set.of(new SimpleGrantedAuthority("ROLE_LEARNER"))
        ));
    }
}
