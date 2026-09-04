package com.adn.dabaeum.user.api;

import static com.atlassian.oai.validator.mockmvc.OpenApiValidationMatchers.openApi;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.adn.dabaeum.common.api.ApiErrorCode;
import com.adn.dabaeum.common.api.ApiException;
import com.adn.dabaeum.common.api.ApiExceptionHandler;
import com.adn.dabaeum.common.config.ClockConfiguration;
import com.adn.dabaeum.common.config.DevBearerSecurityConfiguration;
import com.adn.dabaeum.common.config.JacksonConfiguration;
import com.adn.dabaeum.common.config.SecurityConfiguration;
import com.adn.dabaeum.common.security.AuthenticatedUserPrincipal;
import com.adn.dabaeum.common.web.RequestIdFilter;
import com.adn.dabaeum.user.application.ChangeUserStatusCommand;
import com.adn.dabaeum.user.application.UpdateMeCommand;
import com.adn.dabaeum.user.application.UserApplicationService;
import com.adn.dabaeum.user.domain.User;
import com.adn.dabaeum.user.domain.UserStatus;
import com.adn.dabaeum.testsupport.OpenApiContract;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(UserController.class)
@ActiveProfiles("local")
@TestPropertySource(properties =
    "dabaeum.security.dev.bearer-token=stage2b-task5-test-token")
@Import({
    UserApiMapper.class,
    ApiExceptionHandler.class,
    ClockConfiguration.class,
    SecurityConfiguration.class,
    DevBearerSecurityConfiguration.class,
    JacksonConfiguration.class,
    RequestIdFilter.class
})
class UserMeControllerTest {

    private static final Path CONTRACT = Path.of(
        "docs/api/dabaeum-api-v1.yaml"
    ).toAbsolutePath();
    private static final UUID USER_A = UUID.fromString(
        "55555555-5555-5555-5555-555555555555"
    );
    private static final UUID USER_B = UUID.fromString(
        "66666666-6666-6666-6666-666666666666"
    );
    private static final String REQUEST_ID =
        "ab706c0e-96cf-4d0a-b357-d80752facf1c";

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    UserApplicationService service;

    @Test
    void getsOnlyAuthenticatedPrincipalUserFromMePath() throws Exception {
        when(service.getMe(USER_A)).thenReturn(sampleUser(USER_A));

        mockMvc.perform(get("/api/v1/users/me")
                .with(principal(USER_A, "USER"))
                .header(RequestIdFilter.HEADER_NAME, REQUEST_ID))
            .andExpect(status().isOk())
            .andExpect(header().string(
                RequestIdFilter.HEADER_NAME,
                REQUEST_ID
            ))
            .andExpect(jsonPath("$.data.id").value(USER_A.toString()))
            .andExpect(jsonPath("$.meta.requestId").value(REQUEST_ID))
            .andExpect(openApi().isValid(OpenApiContract.VALIDATOR));

        verify(service).getMe(USER_A);
        verify(service, never()).get(USER_B);
    }

    @Test
    void updatesOnlyAuthenticatedPrincipalUserFromMePath() throws Exception {
        when(service.updateMe(any(), any())).thenReturn(sampleUser(USER_A));

        mockMvc.perform(put("/api/v1/users/me")
                .with(principal(USER_A, "USER"))
                .header(RequestIdFilter.HEADER_NAME, REQUEST_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "본인 변경",
                      "email": null,
                      "phone": "010-2222-3333",
                      "birthDate": "2001-02-03"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(header().string(
                RequestIdFilter.HEADER_NAME,
                REQUEST_ID
            ))
            .andExpect(jsonPath("$.data.id").value(USER_A.toString()))
            .andExpect(jsonPath("$.meta.requestId").value(REQUEST_ID))
            .andExpect(openApi().isValid(OpenApiContract.VALIDATOR));

        ArgumentCaptor<UpdateMeCommand> command =
            ArgumentCaptor.forClass(UpdateMeCommand.class);
        ArgumentCaptor<UUID> userId = ArgumentCaptor.forClass(UUID.class);
        verify(service).updateMe(userId.capture(), command.capture());
        Assertions.assertThat(userId.getValue()).isEqualTo(USER_A);
        Assertions.assertThat(command.getValue().name().value())
            .isEqualTo("본인 변경");
        Assertions.assertThat(command.getValue().email().present()).isTrue();
        Assertions.assertThat(command.getValue().email().value()).isNull();
        Assertions.assertThat(command.getValue().birthDate().value())
            .isEqualTo(LocalDate.of(2001, 2, 3));
    }

    @Test
    void rejectsStringPrincipalAndAnonymousMeAccess() throws Exception {
        mockMvc.perform(get("/api/v1/users/me")
                .with(authentication(new UsernamePasswordAuthenticationToken(
                    "local-platform-admin",
                    "N/A",
                    Set.of(() -> "ROLE_PLATFORM_ADMIN")
                ))))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mockMvc.perform(put("/api/v1/users/me")
                .with(authentication(new UsernamePasswordAuthenticationToken(
                    "local-platform-admin",
                    "N/A",
                    Set.of(() -> "ROLE_PLATFORM_ADMIN")
                )))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"변경\"}"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mockMvc.perform(get("/api/v1/users/me"))
            .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    void doesNotAllowRegularUserToUseAdminUserIdPath() throws Exception {
        mockMvc.perform(get("/api/v1/users/{userId}", USER_B)
                .with(user("regular-user").roles("USER")))
            .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/v1/users/{userId}", USER_B)
                .with(user("regular-user").roles("USER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"변경\"}"))
            .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void rejectsLifecycleAndUnknownSelfUpdateFields() throws Exception {
        mockMvc.perform(put("/api/v1/users/me")
                .with(principal(USER_A, "USER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"SUSPENDED\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
        mockMvc.perform(put("/api/v1/users/me")
                .with(principal(USER_A, "USER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"ADMIN\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
        mockMvc.perform(put("/api/v1/users/me")
                .with(principal(USER_A, "USER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"invalid\"}"))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        verifyNoInteractions(service);
    }

    @Test
    void mapsSelfNotFoundAndEmptyUpdateErrors() throws Exception {
        when(service.getMe(USER_A)).thenThrow(new com.adn.dabaeum.common.api.ApiException(
            org.springframework.http.HttpStatus.NOT_FOUND,
            ApiErrorCode.USER_NOT_FOUND,
            "User not found"
        ));
        mockMvc.perform(get("/api/v1/users/me")
                .with(principal(USER_A, "USER")))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));

        when(service.updateMe(any(), any())).thenThrow(new ApiException(
            org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY,
            ApiErrorCode.VALIDATION_FAILED,
            "Request validation failed"
        ));
        mockMvc.perform(put("/api/v1/users/me")
                .with(principal(USER_A, "USER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
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

    private User sampleUser(UUID userId) {
        Instant instant = Instant.parse("2026-08-04T00:00:00Z");
        return new User(
            userId,
            "본인 사용자",
            "me@example.com",
            "010-0000-0000",
            LocalDate.of(1990, 1, 1),
            UserStatus.ACTIVE,
            null,
            instant,
            instant
        );
    }
}
