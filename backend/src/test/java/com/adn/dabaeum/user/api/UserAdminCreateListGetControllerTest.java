package com.adn.dabaeum.user.api;

import static com.atlassian.oai.validator.mockmvc.OpenApiValidationMatchers.openApi;
import static org.hamcrest.Matchers.matchesPattern;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.adn.dabaeum.common.api.ApiExceptionHandler;
import com.adn.dabaeum.common.config.ClockConfiguration;
import com.adn.dabaeum.common.config.DevBearerSecurityConfiguration;
import com.adn.dabaeum.common.config.JacksonConfiguration;
import com.adn.dabaeum.common.config.SecurityConfiguration;
import com.adn.dabaeum.common.web.RequestIdFilter;
import com.adn.dabaeum.user.application.UserApplicationService;
import com.adn.dabaeum.user.application.UserPage;
import com.adn.dabaeum.user.domain.User;
import com.adn.dabaeum.user.domain.UserStatus;
import com.adn.dabaeum.testsupport.OpenApiContract;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
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

@WebMvcTest(UserController.class)
@ActiveProfiles("local")
@TestPropertySource(properties =
    "dabaeum.security.dev.bearer-token=stage2b-test-token")
@Import({
    UserApiMapper.class,
    ApiExceptionHandler.class,
    ClockConfiguration.class,
    SecurityConfiguration.class,
    DevBearerSecurityConfiguration.class,
    JacksonConfiguration.class,
    RequestIdFilter.class
})
class UserAdminCreateListGetControllerTest {

    private static final Path CONTRACT = Path.of(
        "docs/api/dabaeum-api-v1.yaml"
    ).toAbsolutePath();
    private static final UUID USER_ID = UUID.fromString(
        "22222222-2222-2222-2222-222222222222"
    );
    private static final String REQUEST_ID =
        "8b706c0e-96cf-4d0a-b357-d80752facf1c";

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    UserApplicationService service;

    @Test
    void createsUserWithLocationAndOpenApiResponse() throws Exception {
        when(service.create(any())).thenReturn(sampleUser());

        mockMvc.perform(post("/api/v1/users")
                .with(user("platform-admin").roles("PLATFORM_ADMIN"))
                .header(RequestIdFilter.HEADER_NAME, REQUEST_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "다배움 사용자",
                      "email": "user@example.com",
                      "phone": "010-1234-5678",
                      "birthDate": "2000-01-02",
                      "status": "ACTIVE"
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(header().string(
                "Location",
                "http://localhost/api/v1/users/" + USER_ID
            ))
            .andExpect(header().string(
                RequestIdFilter.HEADER_NAME,
                REQUEST_ID
            ))
            .andExpect(jsonPath("$.data.id").value(USER_ID.toString()))
            .andExpect(jsonPath("$.data.name").value("다배움 사용자"))
            .andExpect(jsonPath("$.meta.requestId").value(REQUEST_ID))
            .andExpect(openApi().isValid(OpenApiContract.VALIDATOR));

        verify(service).create(any());
    }

    @Test
    void listsUsersWithDefaultPageAndOpenApiResponse() throws Exception {
        when(service.list(any())).thenReturn(new UserPage(
            List.of(sampleUser()),
            0,
            20,
            1,
            1
        ));

        mockMvc.perform(get("/api/v1/users")
                .with(user("platform-admin").roles("PLATFORM_ADMIN"))
                .header(RequestIdFilter.HEADER_NAME, REQUEST_ID))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].id").value(USER_ID.toString()))
            .andExpect(jsonPath("$.page.page").value(0))
            .andExpect(jsonPath("$.page.size").value(20))
            .andExpect(jsonPath("$.page.totalElements").value(1))
            .andExpect(jsonPath("$.page.totalPages").value(1))
            .andExpect(jsonPath("$.meta.timestamp")
                .value(matchesPattern(".*\\+09:00$")))
            .andExpect(openApi().isValid(OpenApiContract.VALIDATOR));

        verify(service).list(any());
    }

    @Test
    void getsUserByUuidWithOpenApiResponse() throws Exception {
        when(service.get(USER_ID)).thenReturn(sampleUser());

        mockMvc.perform(get("/api/v1/users/{userId}", USER_ID)
                .with(user("platform-admin").roles("PLATFORM_ADMIN"))
                .header(RequestIdFilter.HEADER_NAME, REQUEST_ID))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.id").value(USER_ID.toString()))
            .andExpect(jsonPath("$.data.withdrawnAt").doesNotExist())
            .andExpect(jsonPath("$.meta.requestId").value(REQUEST_ID))
            .andExpect(openApi().isValid(OpenApiContract.VALIDATOR));
    }

    @Test
    void rejectsRegularUserAndAnonymousAdminPaths() throws Exception {
        mockMvc.perform(post("/api/v1/users")
                .with(user("regular-user").roles("USER")))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/users")
                .with(user("regular-user").roles("USER")))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/users/{userId}", USER_ID)
                .with(user("regular-user").roles("USER")))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/users"))
            .andExpect(status().isUnauthorized());
        verify(service, never()).list(any());
    }

    @Test
    void rejectsInvalidCreateAndMalformedUuid() throws Exception {
        mockMvc.perform(post("/api/v1/users")
                .with(user("platform-admin").roles("PLATFORM_ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\" \"}"))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        mockMvc.perform(get("/api/v1/users/not-a-uuid")
                .with(user("platform-admin").roles("PLATFORM_ADMIN")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("BAD_REQUEST"));

        mockMvc.perform(get("/api/v1/users?page=not-an-int")
                .with(user("platform-admin").roles("PLATFORM_ADMIN")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("BAD_REQUEST"));

        mockMvc.perform(post("/api/v1/users")
                .with(user("platform-admin").roles("PLATFORM_ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"사용자\",\"unknown\":true}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
        verify(service, never()).get(any());
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
}
