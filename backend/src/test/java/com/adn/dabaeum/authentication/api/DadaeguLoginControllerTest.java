package com.adn.dabaeum.authentication.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.adn.dabaeum.authentication.application.DadaeguLoginProperties;
import com.adn.dabaeum.authentication.application.DadaeguLoginService;
import com.adn.dabaeum.authentication.application.LocalAuthResult;
import com.adn.dabaeum.common.api.ApiExceptionHandler;
import com.adn.dabaeum.common.config.ClockConfiguration;
import com.adn.dabaeum.common.config.DevBearerSecurityConfiguration;
import com.adn.dabaeum.common.config.JacksonConfiguration;
import com.adn.dabaeum.common.config.SecurityConfiguration;
import com.adn.dabaeum.common.security.AuthenticatedRole;
import com.adn.dabaeum.common.web.RequestIdFilter;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(DadaeguLoginController.class)
@ActiveProfiles("local")
@TestPropertySource(properties =
    "dabaeum.security.dev.bearer-token=dadaegu-login-controller-test")
@Import({
    ApiExceptionHandler.class,
    ClockConfiguration.class,
    SecurityConfiguration.class,
    DevBearerSecurityConfiguration.class,
    JacksonConfiguration.class,
    RequestIdFilter.class,
    DadaeguLoginControllerTest.Properties.class
})
class DadaeguLoginControllerTest {

    private static final UUID USER_ID = UUID.fromString(
        "11111111-1111-1111-1111-111111111111"
    );
    private static final Instant EXPIRES_AT =
        Instant.parse("2026-09-01T01:00:00Z");
    private static final String REQUEST_ID =
        "22222222-2222-2222-2222-222222222222";

    @Autowired MockMvc mockMvc;
    @MockitoBean DadaeguLoginService service;

    @TestConfiguration
    static class Properties {

        @Bean
        DadaeguLoginProperties dadaeguLoginProperties() throws Exception {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return new DadaeguLoginProperties(
                "SITE-TEST",
                Base64.getEncoder().encodeToString(
                    generator.generateKeyPair().getPrivate().getEncoded()
                )
            );
        }
    }

    @Test
    void configIsPublicAndExposesSiteIdOnly() throws Exception {
        mockMvc.perform(get("/api/v1/auth/dadaegu/config")
                .header(RequestIdFilter.HEADER_NAME, REQUEST_ID))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.siteId").value("SITE-TEST"))
            .andExpect(jsonPath("$.data.privateKey").doesNotExist())
            .andExpect(jsonPath("$.meta.requestId").value(REQUEST_ID));
    }

    @Test
    void loginIsPublicAndReturnsDadaeguTokenSession() throws Exception {
        when(service.login(any(), any(), any(), any())).thenReturn(result());

        mockMvc.perform(post("/api/v1/auth/dadaegu/login")
                .header(RequestIdFilter.HEADER_NAME, REQUEST_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "did": "ZW5jcnlwdGVkLWRpZA==",
                      "name": "ZW5jcnlwdGVkLW5hbWU=",
                      "birthdate": "ZW5jcnlwdGVkLWJpcnRo",
                      "phoneNumber": "ZW5jcnlwdGVkLXBob25l",
                      "ci": "aWdub3JlZA==",
                      "gender": "aWdub3JlZA=="
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.accessToken").value("signed-token"))
            .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
            .andExpect(jsonPath("$.data.expiresIn").value(3600))
            .andExpect(jsonPath("$.data.session.userId").value(USER_ID.toString()))
            .andExpect(jsonPath("$.data.session.provider").value("DADAEGU"))
            .andExpect(jsonPath("$.data.session.roles[0].role").value("LEARNER"))
            .andExpect(jsonPath("$.data.session.expiresAt")
                .value(EXPIRES_AT.toString()))
            .andExpect(jsonPath("$.meta.requestId").value(REQUEST_ID));
    }

    @Test
    void loginRejectsBlankDid() throws Exception {
        mockMvc.perform(post("/api/v1/auth/dadaegu/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"did": ""}
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
            "DADAEGU",
            List.of(new AuthenticatedRole("LEARNER", null)),
            EXPIRES_AT
        );
    }
}
