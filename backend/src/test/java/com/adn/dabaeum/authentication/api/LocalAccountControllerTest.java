package com.adn.dabaeum.authentication.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.adn.dabaeum.authentication.application.LocalAccountApplicationService;
import com.adn.dabaeum.authentication.application.LocalAuthResult;
import com.adn.dabaeum.common.api.ApiExceptionHandler;
import com.adn.dabaeum.common.config.ClockConfiguration;
import com.adn.dabaeum.common.config.DevBearerSecurityConfiguration;
import com.adn.dabaeum.common.config.JacksonConfiguration;
import com.adn.dabaeum.common.config.SecurityConfiguration;
import com.adn.dabaeum.common.security.AuthenticatedRole;
import com.adn.dabaeum.common.web.RequestIdFilter;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(LocalAccountController.class)
@ActiveProfiles("local")
@TestPropertySource(properties =
    "dabaeum.security.dev.bearer-token=local-account-controller-test")
@Import({
    ApiExceptionHandler.class,
    ClockConfiguration.class,
    SecurityConfiguration.class,
    DevBearerSecurityConfiguration.class,
    JacksonConfiguration.class,
    RequestIdFilter.class
})
class LocalAccountControllerTest {

    private static final UUID USER_ID = UUID.fromString(
        "11111111-1111-1111-1111-111111111111"
    );
    private static final Instant EXPIRES_AT =
        Instant.parse("2026-08-12T01:00:00Z");
    private static final String REQUEST_ID =
        "22222222-2222-2222-2222-222222222222";

    @Autowired MockMvc mockMvc;
    @MockitoBean LocalAccountApplicationService service;

    @Test
    void signupIsPublicAndReturnsCreatedTokenSession() throws Exception {
        when(service.signup(any())).thenReturn(result());

        mockMvc.perform(post("/api/v1/auth/signup")
                .header(RequestIdFilter.HEADER_NAME, REQUEST_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "learner@example.com",
                      "password": "learning-2026!",
                      "name": "홍길동",
                      "phone": "010-1234-5678",
                      "birthDate": "1995-05-10"
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(header().string(
                "Location",
                "http://localhost/api/v1/users/" + USER_ID
            ))
            .andExpect(jsonPath("$.data.accessToken").value("signed-token"))
            .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
            .andExpect(jsonPath("$.data.expiresIn").value(3600))
            .andExpect(jsonPath("$.data.session.userId").value(USER_ID.toString()))
            .andExpect(jsonPath("$.data.session.provider").value("LOCAL"))
            .andExpect(jsonPath("$.data.session.roles[0].role").value("LEARNER"))
            .andExpect(jsonPath("$.data.session.roles[0].institutionId")
                .doesNotExist())
            .andExpect(jsonPath("$.data.session.expiresAt")
                .value(EXPIRES_AT.toString()))
            .andExpect(jsonPath("$.data.password").doesNotExist())
            .andExpect(jsonPath("$.data.passwordHash").doesNotExist())
            .andExpect(jsonPath("$.meta.requestId").value(REQUEST_ID));
    }

    @Test
    void loginIsPublicAndReturnsTokenSession() throws Exception {
        when(service.login(any())).thenReturn(result());

        mockMvc.perform(post("/api/v1/auth/login")
                .header(RequestIdFilter.HEADER_NAME, REQUEST_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "learner@example.com",
                      "password": "learning-2026!"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.accessToken").value("signed-token"))
            .andExpect(jsonPath("$.data.session.roles[0].role").value("LEARNER"));
    }

    @Test
    void signupRejectsUnknownPrivilegeFieldsAndInvalidBody() throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "learner@example.com",
                      "password": "learning-2026!",
                      "name": "홍길동",
                      "institutionId": "22222222-2222-2222-2222-222222222222"
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("BAD_REQUEST"));

        mockMvc.perform(post("/api/v1/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email":"not-email","password":"","name":""}
                    """))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    private LocalAuthResult result() {
        return new LocalAuthResult(
            "signed-token",
            "Bearer",
            3600,
            USER_ID,
            "LOCAL",
            List.of(new AuthenticatedRole("LEARNER", null)),
            EXPIRES_AT
        );
    }
}
