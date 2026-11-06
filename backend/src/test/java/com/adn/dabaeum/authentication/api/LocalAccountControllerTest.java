package com.adn.dabaeum.authentication.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.adn.dabaeum.authentication.application.ChangePasswordCommand;
import com.adn.dabaeum.authentication.application.LocalAccountApplicationService;
import com.adn.dabaeum.authentication.application.LocalAuthResult;
import com.adn.dabaeum.common.api.ApiErrorCode;
import com.adn.dabaeum.common.api.ApiException;
import com.adn.dabaeum.common.api.ApiExceptionHandler;
import com.adn.dabaeum.common.config.ClockConfiguration;
import com.adn.dabaeum.common.config.DevBearerSecurityConfiguration;
import com.adn.dabaeum.common.config.LocalDevSecuritySupportConfiguration;
import com.adn.dabaeum.common.config.JacksonConfiguration;
import com.adn.dabaeum.common.config.SecurityConfiguration;
import com.adn.dabaeum.common.security.AuthenticatedRole;
import com.adn.dabaeum.common.security.AuthenticatedUserPrincipal;
import com.adn.dabaeum.common.web.RequestIdFilter;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(LocalAccountController.class)
@ActiveProfiles("local")
@TestPropertySource(properties =
    "dabaeum.security.dev.bearer-token=local-account-controller-test")
@Import({
    ApiExceptionHandler.class,
    ClockConfiguration.class,
    SecurityConfiguration.class,
    DevBearerSecurityConfiguration.class,
    LocalDevSecuritySupportConfiguration.class,
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
                      "birthDate": "1995-05-10",
                      "agreedToUsage": true,
                      "agreedToPrivacy": true,
                      "agreedToMarketing": false
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
                      "agreedToUsage": true,
                      "agreedToPrivacy": true,
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

    @Test
    void signupRejectsEmailWithoutDottedDomainBeforeTheService() throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "a@b",
                      "password": "learning-2026!",
                      "name": "홍길동",
                      "agreedToUsage": true,
                      "agreedToPrivacy": true
                    }
                    """))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
            .andExpect(jsonPath("$.details[0]").value("email"));
        verifyNoInteractions(service);
    }

    @Test
    void changePasswordChangesOwnPasswordAndReturnsSuccessEnvelope() throws Exception {
        mockMvc.perform(put("/api/v1/users/me/password")
                .with(principal(USER_ID, "USER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"currentPassword": "current-pass1",
                     "newPassword": "new-password1"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.reset").value(true));

        Mockito.verify(service).changePassword(
            new ChangePasswordCommand(USER_ID, "current-pass1", "new-password1"));
    }

    @Test
    void 요청의_userId_는_무시되고_로그인_사용자만_바뀐다() throws Exception {
        mockMvc.perform(put("/api/v1/users/me/password")
                .with(principal(USER_ID, "USER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"currentPassword": "current-pass1",
                     "newPassword": "new-password1",
                     "userId": "00000000-0000-0000-0000-000000000099"}
                    """))
            .andExpect(status().isBadRequest());
    }

    @Test
    void changePasswordMapsWrongCurrentPasswordTo401() throws Exception {
        Mockito.doThrow(new ApiException(
                HttpStatus.UNAUTHORIZED,
                ApiErrorCode.AUTHENTICATION_FAILED,
                "Authentication failed"))
            .when(service).changePassword(any());

        mockMvc.perform(put("/api/v1/users/me/password")
                .with(principal(USER_ID, "USER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"currentPassword": "wrong-pass1",
                     "newPassword": "new-password1"}
                    """))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("AUTHENTICATION_FAILED"));
    }

    @Test
    void changePasswordMapsMissingLocalAccountTo404() throws Exception {
        // 다대구 DID 로만 가입한 사용자는 로컬 자격증명이 없다.
        Mockito.doThrow(new ApiException(
                HttpStatus.NOT_FOUND,
                ApiErrorCode.IDENTITY_NOT_FOUND,
                "Local account not found"))
            .when(service).changePassword(any());

        mockMvc.perform(put("/api/v1/users/me/password")
                .with(principal(USER_ID, "USER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"currentPassword": "current-pass1",
                     "newPassword": "new-password1"}
                    """))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("IDENTITY_NOT_FOUND"));
    }

    private RequestPostProcessor principal(
        UUID userId,
        String role
    ) {
        return authentication(new UsernamePasswordAuthenticationToken(
            new AuthenticatedUserPrincipal(userId, Set.of("ROLE_" + role)),
            "N/A",
            Set.of(new SimpleGrantedAuthority("ROLE_" + role))
        ));
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
