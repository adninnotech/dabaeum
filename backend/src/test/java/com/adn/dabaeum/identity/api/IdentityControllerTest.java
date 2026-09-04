package com.adn.dabaeum.identity.api;

import static com.atlassian.oai.validator.mockmvc.OpenApiValidationMatchers.openApi;
import static org.mockito.ArgumentMatchers.any;
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
import com.adn.dabaeum.common.config.ClockConfiguration;
import com.adn.dabaeum.common.config.DevBearerSecurityConfiguration;
import com.adn.dabaeum.common.config.JacksonConfiguration;
import com.adn.dabaeum.common.config.SecurityConfiguration;
import com.adn.dabaeum.common.web.RequestIdFilter;
import com.adn.dabaeum.identity.application.IdentityApplicationService;
import com.adn.dabaeum.identity.domain.IdentityProvider;
import com.adn.dabaeum.identity.domain.UserIdentity;
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

@WebMvcTest(IdentityController.class)
@ActiveProfiles("local")
@TestPropertySource(properties =
    "dabaeum.security.dev.bearer-token=stage3-identity-test-token")
@Import({
    IdentityApiMapper.class,
    ApiExceptionHandler.class,
    ClockConfiguration.class,
    SecurityConfiguration.class,
    DevBearerSecurityConfiguration.class,
    JacksonConfiguration.class,
    RequestIdFilter.class
})
class IdentityControllerTest {

    private static final Path CONTRACT = Path.of(
        "docs/api/dabaeum-api-v1.yaml"
    ).toAbsolutePath();
    private static final UUID USER_ID = UUID.fromString(
        "11111111-1111-1111-1111-111111111111"
    );
    private static final UUID IDENTITY_ID = UUID.fromString(
        "22222222-2222-2222-2222-222222222222"
    );
    private static final String REQUEST_ID =
        "8b706c0e-96cf-4d0a-b357-d80752facf1c";

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    IdentityApplicationService service;

    @Test
    void listsIdentitiesWithOnlyPublicFieldsAndOpenApiResponse() throws Exception {
        when(service.list(USER_ID)).thenReturn(List.of(identity()));

        mockMvc.perform(get("/api/v1/users/{userId}/identities", USER_ID)
                .with(user("platform-admin").roles("PLATFORM_ADMIN"))
                .header(RequestIdFilter.HEADER_NAME, REQUEST_ID))
            .andExpect(status().isOk())
            .andExpect(header().string(
                RequestIdFilter.HEADER_NAME,
                REQUEST_ID
            ))
            .andExpect(jsonPath("$.data[0].id")
                .value(IDENTITY_ID.toString()))
            .andExpect(jsonPath("$.data[0].provider").value("DADAEGU"))
            .andExpect(jsonPath("$.data[0].providerSubject").doesNotExist())
            .andExpect(jsonPath("$.data[0].externalDid").doesNotExist())
            .andExpect(jsonPath("$.data[0].metadata").doesNotExist())
            .andExpect(jsonPath("$.meta.requestId").value(REQUEST_ID))
            .andExpect(openApi().isValid(OpenApiContract.VALIDATOR));

        verify(service).list(USER_ID);
    }

    @Test
    void linksIdentityAndReturnsPublicResponse() throws Exception {
        when(service.link(any())).thenReturn(identity());

        mockMvc.perform(post("/api/v1/users/{userId}/identities", USER_ID)
                .with(user("platform-admin").roles("PLATFORM_ADMIN"))
                .header(RequestIdFilter.HEADER_NAME, REQUEST_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "provider": "DADAEGU",
                      "providerSubject": "subject-raw-value",
                      "externalDid": "did:example:123",
                      "verified": true
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(header().string(
                "Location",
                "http://localhost/api/v1/users/" + USER_ID
                    + "/identities/" + IDENTITY_ID
            ))
            .andExpect(jsonPath("$.data.id")
                .value(IDENTITY_ID.toString()))
            .andExpect(jsonPath("$.data.provider").value("DADAEGU"))
            .andExpect(jsonPath("$.data.providerSubject").doesNotExist())
            .andExpect(jsonPath("$.data.externalDid").doesNotExist())
            .andExpect(jsonPath("$.data.metadata").doesNotExist())
            .andExpect(openApi().isValid(OpenApiContract.VALIDATOR));

        verify(service).link(any());
    }

    @Test
    void unlinksIdentityAndReturnsPublicResponse() throws Exception {
        when(service.unlink(USER_ID, IDENTITY_ID)).thenReturn(identity());

        mockMvc.perform(delete(
                "/api/v1/users/{userId}/identities/{identityId}",
                USER_ID,
                IDENTITY_ID
            )
                .with(user("platform-admin").roles("PLATFORM_ADMIN"))
                .header(RequestIdFilter.HEADER_NAME, REQUEST_ID))
            .andExpect(status().isOk())
            .andExpect(header().string(
                RequestIdFilter.HEADER_NAME,
                REQUEST_ID
            ))
            .andExpect(jsonPath("$.data.id")
                .value(IDENTITY_ID.toString()))
            .andExpect(openApi().isValid(OpenApiContract.VALIDATOR));

        verify(service).unlink(USER_ID, IDENTITY_ID);
    }

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get(
                "/api/v1/users/{userId}/identities",
                USER_ID
            ))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void allowsOnlyPlatformAdmin() throws Exception {
        mockMvc.perform(get(
                "/api/v1/users/{userId}/identities",
                USER_ID
            ).with(user("regular-user").roles("USER")))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mockMvc.perform(post(
                "/api/v1/users/{userId}/identities",
                USER_ID
            ).with(user("regular-user").roles("USER")))
            .andExpect(status().isForbidden());

        mockMvc.perform(delete(
                "/api/v1/users/{userId}/identities/{identityId}",
                USER_ID,
                IDENTITY_ID
            ).with(user("regular-user").roles("USER")))
            .andExpect(status().isForbidden());

        verify(service, never()).list(any());
        verify(service, never()).link(any());
        verify(service, never()).unlink(any(), any());
    }

    @Test
    void mapsMissingUserToNotFound() throws Exception {
        when(service.list(USER_ID)).thenThrow(new ApiException(
            HttpStatus.NOT_FOUND,
            ApiErrorCode.USER_NOT_FOUND,
            "User not found"
        ));

        mockMvc.perform(get(
                "/api/v1/users/{userId}/identities",
                USER_ID
            ).with(user("platform-admin").roles("PLATFORM_ADMIN")))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }

    @Test
    void mapsMissingIdentityToNotFound() throws Exception {
        when(service.unlink(USER_ID, IDENTITY_ID)).thenThrow(new ApiException(
            HttpStatus.NOT_FOUND,
            ApiErrorCode.IDENTITY_NOT_FOUND,
            "Identity not found"
        ));

        mockMvc.perform(delete(
                "/api/v1/users/{userId}/identities/{identityId}",
                USER_ID,
                IDENTITY_ID
            ).with(user("platform-admin").roles("PLATFORM_ADMIN")))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("IDENTITY_NOT_FOUND"));
    }

    @Test
    void mapsDuplicateIdentityToConflict() throws Exception {
        when(service.link(any())).thenThrow(new ApiException(
            HttpStatus.CONFLICT,
            ApiErrorCode.IDENTITY_CONFLICT,
            "Identity already exists"
        ));

        mockMvc.perform(post(
                "/api/v1/users/{userId}/identities",
                USER_ID
            )
                .with(user("platform-admin").roles("PLATFORM_ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "provider": "DADAEGU",
                      "providerSubject": "duplicate-subject",
                      "verified": true
                    }
                    """))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("IDENTITY_CONFLICT"));
    }

    @Test
    void mapsLastIdentityProtectionToConflict() throws Exception {
        when(service.unlink(USER_ID, IDENTITY_ID)).thenThrow(new ApiException(
            HttpStatus.CONFLICT,
            ApiErrorCode.IDENTITY_REQUIRED,
            "At least one verified identity is required"
        ));

        mockMvc.perform(delete(
                "/api/v1/users/{userId}/identities/{identityId}",
                USER_ID,
                IDENTITY_ID
            ).with(user("platform-admin").roles("PLATFORM_ADMIN")))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("IDENTITY_REQUIRED"));
    }

    @Test
    void mapsUnverifiedIdentityToUnprocessableEntity() throws Exception {
        when(service.link(any())).thenThrow(new ApiException(
            HttpStatus.UNPROCESSABLE_ENTITY,
            ApiErrorCode.IDENTITY_NOT_VERIFIED,
            "Identity verification is required"
        ));

        mockMvc.perform(post(
                "/api/v1/users/{userId}/identities",
                USER_ID
            )
                .with(user("platform-admin").roles("PLATFORM_ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "provider": "DID",
                      "providerSubject": "unverified-subject",
                      "verified": false
                    }
                    """))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code")
                .value("IDENTITY_NOT_VERIFIED"));
    }

    @Test
    void rejectsInvalidLinkRequestWithValidationContract() throws Exception {
        mockMvc.perform(post(
                "/api/v1/users/{userId}/identities",
                USER_ID
            )
                .with(user("platform-admin").roles("PLATFORM_ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "provider": "DID",
                      "providerSubject": " ",
                      "verified": true
                    }
                    """))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        verify(service, never()).link(any());
    }

    private UserIdentity identity() {
        Instant instant = Instant.parse("2026-08-04T00:00:00Z");
        return new UserIdentity(
            IDENTITY_ID,
            USER_ID,
            IdentityProvider.DADAEGU,
            "subject-raw-value",
            "did:example:123",
            instant,
            "{\"private\":true}",
            instant,
            instant
        );
    }
}
