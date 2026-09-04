package com.adn.dabaeum.role.api;

import static com.atlassian.oai.validator.mockmvc.OpenApiValidationMatchers.openApi;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.adn.dabaeum.common.api.ApiErrorCode;
import com.adn.dabaeum.common.api.ApiException;
import com.adn.dabaeum.common.api.ApiExceptionHandler;
import com.adn.dabaeum.authorization.application.AuthorizationPolicy;
import com.adn.dabaeum.common.config.ClockConfiguration;
import com.adn.dabaeum.common.config.DevBearerSecurityConfiguration;
import com.adn.dabaeum.common.config.JacksonConfiguration;
import com.adn.dabaeum.common.config.SecurityConfiguration;
import com.adn.dabaeum.common.web.RequestIdFilter;
import com.adn.dabaeum.role.application.RoleApplicationService;
import com.adn.dabaeum.role.domain.UserRole;
import com.adn.dabaeum.role.domain.UserRoleAssignment;
import com.adn.dabaeum.testsupport.OpenApiContract;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(RoleController.class)
@ActiveProfiles("local")
@TestPropertySource(properties =
    "dabaeum.security.dev.bearer-token=stage3-role-test-token")
@Import({
    RoleApiMapper.class,
    AuthorizationPolicy.class,
    ApiExceptionHandler.class,
    ClockConfiguration.class,
    SecurityConfiguration.class,
    DevBearerSecurityConfiguration.class,
    JacksonConfiguration.class,
    RequestIdFilter.class
})
class RoleControllerTest {

    private static final Path CONTRACT = Path.of(
        "docs/api/dabaeum-api-v1.yaml"
    ).toAbsolutePath();
    private static final UUID USER_ID = UUID.fromString(
        "11111111-1111-1111-1111-111111111111"
    );
    private static final UUID ROLE_ID = UUID.fromString(
        "22222222-2222-2222-2222-222222222222"
    );
    private static final UUID INSTITUTION_ID = UUID.fromString(
        "33333333-3333-3333-3333-333333333333"
    );
    private static final String REQUEST_ID =
        "8b706c0e-96cf-4d0a-b357-d80752facf1c";

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    RoleApplicationService service;

    @Test
    void listsGlobalAndInstitutionScopedRoles() throws Exception {
        when(service.list(USER_ID)).thenReturn(List.of(
            assignment(UserRole.PLATFORM_ADMIN, null),
            assignment(UserRole.INSTRUCTOR, INSTITUTION_ID)
        ));

        mockMvc.perform(get("/api/v1/users/{userId}/roles", USER_ID)
                .with(user("platform-admin").roles("PLATFORM_ADMIN"))
                .header(RequestIdFilter.HEADER_NAME, REQUEST_ID))
            .andExpect(status().isOk())
            .andExpect(header().string(
                RequestIdFilter.HEADER_NAME,
                REQUEST_ID
            ))
            .andExpect(jsonPath("$.data[0].role")
                .value("PLATFORM_ADMIN"))
            .andExpect(jsonPath("$.data[0].institutionId")
                .doesNotExist())
            .andExpect(jsonPath("$.data[1].role")
                .value("INSTRUCTOR"))
            .andExpect(jsonPath("$.data[1].institutionId")
                .value(INSTITUTION_ID.toString()))
            .andExpect(jsonPath("$.data[0].userId").doesNotExist())
            .andExpect(jsonPath("$.meta.requestId").value(REQUEST_ID))
            .andExpect(openApi().isValid(OpenApiContract.VALIDATOR));

        verify(service).list(USER_ID);
    }

    @Test
    void assignsScopedRoleAndReturnsCreatedResource() throws Exception {
        when(service.assign(any())).thenReturn(
            assignment(UserRole.INSTRUCTOR, INSTITUTION_ID)
        );

        mockMvc.perform(post("/api/v1/users/{userId}/roles", USER_ID)
                .with(user("platform-admin").roles("PLATFORM_ADMIN"))
                .header(RequestIdFilter.HEADER_NAME, REQUEST_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "role": "INSTRUCTOR",
                      "institutionId": "33333333-3333-3333-3333-333333333333"
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(header().string(
                "Location",
                "http://localhost/api/v1/users/" + USER_ID
                    + "/roles/" + ROLE_ID
            ))
            .andExpect(jsonPath("$.data.id").value(ROLE_ID.toString()))
            .andExpect(jsonPath("$.data.role").value("INSTRUCTOR"))
            .andExpect(jsonPath("$.data.institutionId")
                .value(INSTITUTION_ID.toString()))
            .andExpect(jsonPath("$.data.createdAt")
                .value("2026-08-04T00:00:00Z"))
            .andExpect(openApi().isValid(OpenApiContract.VALIDATOR));

        verify(service).assign(any());
    }

    @Test
    void revokesRoleAndReturnsDeletedResource() throws Exception {
        when(service.revoke(USER_ID, ROLE_ID)).thenReturn(
            assignment(UserRole.INSTRUCTOR, INSTITUTION_ID)
        );

        mockMvc.perform(delete(
                "/api/v1/users/{userId}/roles/{roleId}",
                USER_ID,
                ROLE_ID
            )
                .with(user("platform-admin").roles("PLATFORM_ADMIN"))
                .header(RequestIdFilter.HEADER_NAME, REQUEST_ID))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.id").value(ROLE_ID.toString()))
            .andExpect(jsonPath("$.data.role").value("INSTRUCTOR"))
            .andExpect(jsonPath("$.meta.requestId").value(REQUEST_ID))
            .andExpect(openApi().isValid(OpenApiContract.VALIDATOR));

        verify(service).revoke(USER_ID, ROLE_ID);
    }

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/users/{userId}/roles", USER_ID))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void allowsOnlyPlatformAdminForAllRoleOperations() throws Exception {
        mockMvc.perform(get("/api/v1/users/{userId}/roles", USER_ID)
                .with(user("regular-user").roles("USER")))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mockMvc.perform(post("/api/v1/users/{userId}/roles", USER_ID)
                .with(user("regular-user").roles("USER")))
            .andExpect(status().isForbidden());

        mockMvc.perform(delete(
                "/api/v1/users/{userId}/roles/{roleId}",
                USER_ID,
                ROLE_ID
            ).with(user("regular-user").roles("USER")))
            .andExpect(status().isForbidden());

        verify(service, never()).list(any());
        verify(service, never()).assign(any());
        verify(service, never()).revoke(any(), any());
    }

    @Test
    void mapsWithdrawnUserToForbidden() throws Exception {
        when(service.list(USER_ID)).thenThrow(new ApiException(
            HttpStatus.FORBIDDEN,
            ApiErrorCode.USER_STATUS_FORBIDDEN,
            "Withdrawn users cannot manage roles"
        ));

        mockMvc.perform(get("/api/v1/users/{userId}/roles", USER_ID)
                .with(user("platform-admin").roles("PLATFORM_ADMIN")))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code")
                .value("USER_STATUS_FORBIDDEN"));
    }

    @Test
    void mapsMissingUserToNotFound() throws Exception {
        when(service.list(USER_ID)).thenThrow(new ApiException(
            HttpStatus.NOT_FOUND,
            ApiErrorCode.USER_NOT_FOUND,
            "User not found"
        ));

        mockMvc.perform(get("/api/v1/users/{userId}/roles", USER_ID)
                .with(user("platform-admin").roles("PLATFORM_ADMIN")))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }

    @Test
    void mapsDuplicateRoleToConflict() throws Exception {
        when(service.assign(any())).thenThrow(new ApiException(
            HttpStatus.CONFLICT,
            ApiErrorCode.ROLE_ASSIGNMENT_CONFLICT,
            "Role already assigned"
        ));

        mockMvc.perform(post("/api/v1/users/{userId}/roles", USER_ID)
                .with(user("platform-admin").roles("PLATFORM_ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"PLATFORM_ADMIN\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code")
                .value("ROLE_ASSIGNMENT_CONFLICT"));
    }

    @Test
    void mapsMissingRequiredRoleToConflict() throws Exception {
        when(service.revoke(USER_ID, ROLE_ID)).thenThrow(new ApiException(
            HttpStatus.CONFLICT,
            ApiErrorCode.ROLE_REQUIRED,
            "At least one role is required"
        ));

        mockMvc.perform(delete(
                "/api/v1/users/{userId}/roles/{roleId}",
                USER_ID,
                ROLE_ID
            ).with(user("platform-admin").roles("PLATFORM_ADMIN")))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("ROLE_REQUIRED"));
    }

    @Test
    void rejectsMissingRoleRequestField() throws Exception {
        mockMvc.perform(post("/api/v1/users/{userId}/roles", USER_ID)
                .with(user("platform-admin").roles("PLATFORM_ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        verify(service, never()).assign(any());
    }

    private UserRoleAssignment assignment(
        UserRole role,
        UUID institutionId
    ) {
        return new UserRoleAssignment(
            ROLE_ID,
            USER_ID,
            institutionId,
            role,
            Instant.parse("2026-08-04T00:00:00Z")
        );
    }
}
