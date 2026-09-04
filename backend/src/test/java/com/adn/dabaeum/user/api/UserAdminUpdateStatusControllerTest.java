package com.adn.dabaeum.user.api;

import static com.atlassian.oai.validator.mockmvc.OpenApiValidationMatchers.openApi;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
import com.adn.dabaeum.common.web.RequestIdFilter;
import com.adn.dabaeum.user.application.ChangeUserStatusCommand;
import com.adn.dabaeum.user.application.UpdateUserCommand;
import com.adn.dabaeum.user.application.UserApplicationService;
import com.adn.dabaeum.user.domain.User;
import com.adn.dabaeum.user.domain.UserStatus;
import com.adn.dabaeum.testsupport.OpenApiContract;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(UserController.class)
@ActiveProfiles("local")
@TestPropertySource(properties =
    "dabaeum.security.dev.bearer-token=stage2b-task4-test-token")
@Import({
    UserApiMapper.class,
    ApiExceptionHandler.class,
    ClockConfiguration.class,
    SecurityConfiguration.class,
    DevBearerSecurityConfiguration.class,
    JacksonConfiguration.class,
    RequestIdFilter.class
})
class UserAdminUpdateStatusControllerTest {

    private static final Path CONTRACT = Path.of(
        "docs/api/dabaeum-api-v1.yaml"
    ).toAbsolutePath();
    private static final UUID USER_ID = UUID.fromString(
        "33333333-3333-3333-3333-333333333333"
    );
    private static final String REQUEST_ID =
        "9b706c0e-96cf-4d0a-b357-d80752facf1c";

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    UserApplicationService service;

    @Test
    void updatesProfileForPlatformAdminWithPresenceSemantics() throws Exception {
        when(service.updateProfile(any())).thenReturn(sampleUser());

        mockMvc.perform(put("/api/v1/users/{userId}", USER_ID)
                .with(user("platform-admin").roles("PLATFORM_ADMIN"))
                .header(RequestIdFilter.HEADER_NAME, REQUEST_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "변경 사용자",
                      "email": null,
                      "phone": "010-2222-3333"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(header().string(
                RequestIdFilter.HEADER_NAME,
                REQUEST_ID
            ))
            .andExpect(jsonPath("$.data.id").value(USER_ID.toString()))
            .andExpect(jsonPath("$.meta.requestId").value(REQUEST_ID))
            .andExpect(openApi().isValid(OpenApiContract.VALIDATOR));

        ArgumentCaptor<UpdateUserCommand> command =
            ArgumentCaptor.forClass(UpdateUserCommand.class);
        verify(service).updateProfile(command.capture());
        Assertions.assertThat(command.getValue().userId()).isEqualTo(USER_ID);
        Assertions.assertThat(command.getValue().name().present()).isTrue();
        Assertions.assertThat(command.getValue().name().value())
            .isEqualTo("변경 사용자");
        Assertions.assertThat(command.getValue().email().present()).isTrue();
        Assertions.assertThat(command.getValue().email().value()).isNull();
        Assertions.assertThat(command.getValue().birthDate().present()).isFalse();
    }

    @Test
    void changesStatusForPlatformAdminWithRequestIdAndOpenApi() throws Exception {
        when(service.changeStatus(any())).thenReturn(sampleWithdrawnUser());

        mockMvc.perform(patch("/api/v1/users/{userId}/status", USER_ID)
                .with(user("platform-admin").roles("PLATFORM_ADMIN"))
                .header(RequestIdFilter.HEADER_NAME, REQUEST_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"WITHDRAWN\"}"))
            .andExpect(status().isOk())
            .andExpect(header().string(
                RequestIdFilter.HEADER_NAME,
                REQUEST_ID
            ))
            .andExpect(jsonPath("$.data.status").value("WITHDRAWN"))
            .andExpect(jsonPath("$.data.withdrawnAt").exists())
            .andExpect(openApi().isValid(OpenApiContract.VALIDATOR));

        ArgumentCaptor<ChangeUserStatusCommand> command =
            ArgumentCaptor.forClass(ChangeUserStatusCommand.class);
        verify(service).changeStatus(command.capture());
        Assertions.assertThat(command.getValue().userId()).isEqualTo(USER_ID);
        Assertions.assertThat(command.getValue().status())
            .isEqualTo(UserStatus.WITHDRAWN);
    }

    @Test
    void rejectsNonAdminAndAnonymousUpdatePaths() throws Exception {
        mockMvc.perform(put("/api/v1/users/{userId}", USER_ID)
                .with(user("regular-user").roles("USER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"변경\"}"))
            .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/v1/users/{userId}/status", USER_ID)
                .with(user("regular-user").roles("USER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"ACTIVE\"}"))
            .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/v1/users/{userId}", USER_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"변경\"}"))
            .andExpect(status().isUnauthorized());
        verifyNoServiceInteractions();
    }

    @Test
    void rejectsUnknownProfileFieldsAndInvalidStatusBody() throws Exception {
        mockMvc.perform(put("/api/v1/users/{userId}", USER_ID)
                .with(user("platform-admin").roles("PLATFORM_ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"SUSPENDED\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
        mockMvc.perform(patch("/api/v1/users/{userId}/status", USER_ID)
                .with(user("platform-admin").roles("PLATFORM_ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"금지\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
        mockMvc.perform(patch("/api/v1/users/{userId}/status", USER_ID)
                .with(user("platform-admin").roles("PLATFORM_ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":null}"))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mockMvc.perform(patch("/api/v1/users/{userId}/status", USER_ID)
                .with(user("platform-admin").roles("PLATFORM_ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"UNKNOWN\"}"))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        verifyNoServiceInteractions();
    }

    @Test
    void mapsNotFoundAndConflictErrors() throws Exception {
        when(service.updateProfile(any())).thenThrow(new ApiException(
            HttpStatus.NOT_FOUND,
            ApiErrorCode.USER_NOT_FOUND,
            "User not found"
        ));
        mockMvc.perform(put("/api/v1/users/{userId}", USER_ID)
                .with(user("platform-admin").roles("PLATFORM_ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"사용자\"}"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));

        when(service.changeStatus(any())).thenThrow(new ApiException(
            HttpStatus.CONFLICT,
            ApiErrorCode.USER_STATUS_CONFLICT,
            "User status cannot be changed"
        ));
        mockMvc.perform(patch("/api/v1/users/{userId}/status", USER_ID)
                .with(user("platform-admin").roles("PLATFORM_ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"ACTIVE\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("USER_STATUS_CONFLICT"));
    }

    private void verifyNoServiceInteractions() {
        org.mockito.Mockito.verifyNoInteractions(service);
    }

    private User sampleUser() {
        Instant instant = Instant.parse("2026-08-04T00:00:00Z");
        return new User(
            USER_ID,
            "다배움 사용자",
            "user@example.com",
            "010-1234-5678",
            LocalDate.of(2000, 1, 2),
            UserStatus.ACTIVE,
            null,
            instant,
            instant
        );
    }

    private User sampleWithdrawnUser() {
        Instant instant = Instant.parse("2026-08-04T00:00:00Z");
        return new User(
            USER_ID,
            "다배움 사용자",
            "user@example.com",
            "010-1234-5678",
            LocalDate.of(2000, 1, 2),
            UserStatus.WITHDRAWN,
            instant,
            Instant.parse("2026-08-01T00:00:00Z"),
            instant
        );
    }
}
