package com.adn.dabaeum.user.api;

import static org.assertj.core.api.Assertions.assertThat;
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
import com.adn.dabaeum.common.config.LocalDevSecuritySupportConfiguration;
import com.adn.dabaeum.common.config.JacksonConfiguration;
import com.adn.dabaeum.common.config.SecurityConfiguration;
import com.adn.dabaeum.common.web.RequestIdFilter;
import com.adn.dabaeum.role.domain.UserRole;
import com.adn.dabaeum.user.application.ListUsersQuery;
import com.adn.dabaeum.user.application.UserApplicationService;
import com.adn.dabaeum.user.application.UserLastLoginFilter;
import com.adn.dabaeum.user.application.UserPage;
import com.adn.dabaeum.user.application.UserView;
import com.adn.dabaeum.user.domain.Gender;
import com.adn.dabaeum.user.domain.User;
import com.adn.dabaeum.user.domain.UserStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
    LocalDevSecuritySupportConfiguration.class,
    JacksonConfiguration.class,
    RequestIdFilter.class
})
class UserAdminCreateListGetControllerTest {

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
            .andExpect(jsonPath("$.meta.requestId").value(REQUEST_ID));

        verify(service).create(any());
    }

    @Test
    void listsUsersWithDefaultPageAndOpenApiResponse() throws Exception {
        when(service.list(any())).thenReturn(new UserPage(
            List.of(new UserView(sampleUser(), List.of())),
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
                .value(matchesPattern(".*\\+09:00$")));

        verify(service).list(any());
    }

    @Test
    void listResponseIncludesEnrollmentAndCourseCounts() throws Exception {
        when(service.list(any())).thenReturn(new UserPage(
            List.of(new UserView(sampleUser(), List.of(), 4L, 2L)),
            0,
            20,
            1,
            1
        ));

        mockMvc.perform(get("/api/v1/users")
                .with(user("platform-admin").roles("PLATFORM_ADMIN"))
                .header(RequestIdFilter.HEADER_NAME, REQUEST_ID))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].enrollmentCount").value(4))
            .andExpect(jsonPath("$.data[0].courseCount").value(2));
    }

    @Test
    void passesStudentListFiltersToService() throws Exception {
        when(service.list(any())).thenReturn(new UserPage(List.of(), 0, 10, 0, 0));

        mockMvc.perform(get("/api/v1/users")
                .param("page", "0")
                .param("size", "10")
                .param("role", "LEARNER")
                .param("sort", "enrollmentCount,desc")
                .param("gender", "UNDISCLOSED")
                .param("hasEnrollment", "false")
                .param("lastLogin", "OVER_90")
                .param("createdFrom", "2026-09-24")
                .param("createdTo", "2026-10-23")
                .with(user("platform-admin").roles("PLATFORM_ADMIN"))
                .header(RequestIdFilter.HEADER_NAME, REQUEST_ID))
            .andExpect(status().isOk());

        ArgumentCaptor<ListUsersQuery> query = ArgumentCaptor.forClass(ListUsersQuery.class);
        verify(service).list(query.capture());
        assertThat(query.getValue()).isEqualTo(new ListUsersQuery(
            0, 10, "enrollmentCount,desc", null, null, UserRole.LEARNER,
            Gender.UNDISCLOSED, false, UserLastLoginFilter.OVER_90,
            LocalDate.parse("2026-09-24"), LocalDate.parse("2026-10-23")
        ));
    }

    @Test
    void rejectsMalformedStudentListFilters() throws Exception {
        for (String[] param : new String[][] {
            {"gender", "OTHER"},
            {"lastLogin", "WITHIN_14"},
            {"hasEnrollment", "maybe"},
            {"createdFrom", "2026/10/01"}
        }) {
            mockMvc.perform(get("/api/v1/users")
                    .param(param[0], param[1])
                    .with(user("platform-admin").roles("PLATFORM_ADMIN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
        }
        verify(service, never()).list(any());
    }

    @Test
    void getsUserByUuidWithOpenApiResponse() throws Exception {
        when(service.get(USER_ID)).thenReturn(new UserView(sampleUser(), List.of()));

        mockMvc.perform(get("/api/v1/users/{userId}", USER_ID)
                .with(user("platform-admin").roles("PLATFORM_ADMIN"))
                .header(RequestIdFilter.HEADER_NAME, REQUEST_ID))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.id").value(USER_ID.toString()))
            .andExpect(jsonPath("$.data.withdrawnAt").doesNotExist())
            .andExpect(jsonPath("$.meta.requestId").value(REQUEST_ID));
    }

    @Test
    void getResponseNeverIncludesListOnlyCounts() throws Exception {
        // 서비스가 실수로 값을 채워 돌려줘도 회원 상세 응답에는 키 자체가 없어야 한다.
        when(service.get(USER_ID)).thenReturn(new UserView(sampleUser(), List.of(), 4L, 2L));

        mockMvc.perform(get("/api/v1/users/{userId}", USER_ID)
                .with(user("platform-admin").roles("PLATFORM_ADMIN"))
                .header(RequestIdFilter.HEADER_NAME, REQUEST_ID))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.enrollmentCount").doesNotExist())
            .andExpect(jsonPath("$.data.courseCount").doesNotExist());
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
