package com.adn.dabaeum.course.api;

import static com.atlassian.oai.validator.mockmvc.OpenApiValidationMatchers.openApi;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import com.adn.dabaeum.common.security.AuthenticatedRole;
import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import com.adn.dabaeum.common.security.CurrentUserProvider;
import com.adn.dabaeum.common.web.RequestIdFilter;
import com.adn.dabaeum.course.application.CourseSessionApplicationService;
import com.adn.dabaeum.course.application.CourseSessionPage;
import com.adn.dabaeum.course.application.ListCourseSessionsQuery;
import com.adn.dabaeum.course.application.UpdateCourseSessionCommand;
import com.adn.dabaeum.course.domain.CourseSession;
import com.adn.dabaeum.course.domain.CourseSessionStatus;
import com.adn.dabaeum.testsupport.OpenApiContract;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
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

@WebMvcTest(CourseSessionController.class)
@ActiveProfiles("local")
@TestPropertySource(properties =
    "dabaeum.security.dev.bearer-token=course-session-task6-token")
@Import({
    CourseSessionApiMapper.class,
    ApiExceptionHandler.class,
    ClockConfiguration.class,
    SecurityConfiguration.class,
    DevBearerSecurityConfiguration.class,
    JacksonConfiguration.class,
    RequestIdFilter.class
})
class CourseSessionControllerTest {

    private static final Path CONTRACT =
        Path.of("docs/api/dabaeum-api-v1.yaml").toAbsolutePath();
    private static final UUID COURSE_ID = UUID.fromString(
        "33333333-3333-3333-3333-333333333333"
    );
    private static final UUID SESSION_ID = UUID.fromString(
        "55555555-5555-5555-5555-555555555555"
    );
    private static final UUID INSTITUTION_ID = UUID.fromString(
        "22222222-2222-2222-2222-222222222222"
    );
    private static final String REQUEST_ID =
        "9b706c0e-96cf-4d0a-b357-d80752facf1c";

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    CourseSessionApplicationService service;

    @MockitoBean
    CurrentUserProvider currentUserProvider;

    @Test
    void listsSessionsAndMapsDefaultQuery() throws Exception {
        when(service.list(any())).thenReturn(new CourseSessionPage(
            List.of(session()), 0, 20, 1, 1));

        mockMvc.perform(get("/api/v1/courses/{courseId}/sessions", COURSE_ID)
                .with(user("reader").roles("USER"))
                .header(RequestIdFilter.HEADER_NAME, REQUEST_ID))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].sessionNo").value(1))
            .andExpect(jsonPath("$.page.totalElements").value(1))
            .andExpect(jsonPath("$.meta.requestId").value(REQUEST_ID))
            .andExpect(openApi().isValid(OpenApiContract.VALIDATOR));

        verify(service).list(eq(new ListCourseSessionsQuery(
            COURSE_ID, 0, 20, "createdAt,desc")));
    }

    @Test
    void createsSessionWithLocationAndUpdatesPresence() throws Exception {
        when(currentUserProvider.requireContext()).thenReturn(managerContext());
        when(service.create(any(), any())).thenReturn(session());
        when(service.update(any(), any())).thenReturn(session());

        mockMvc.perform(post("/api/v1/courses/{courseId}/sessions", COURSE_ID)
                .with(user("manager").roles("INSTITUTION_ADMIN"))
                .header(RequestIdFilter.HEADER_NAME, REQUEST_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(validCreateJson()))
            .andExpect(status().isCreated())
            .andExpect(header().string(
                "Location", "/api/v1/sessions/" + SESSION_ID))
            .andExpect(jsonPath("$.data.id").value(SESSION_ID.toString()))
            .andExpect(openApi().isValid(OpenApiContract.VALIDATOR));

        mockMvc.perform(put("/api/v1/sessions/{sessionId}", SESSION_ID)
                .with(user("manager").roles("INSTITUTION_ADMIN"))
                .header(RequestIdFilter.HEADER_NAME, REQUEST_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"location\":null,\"status\":\"OPEN\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.id").value(SESSION_ID.toString()))
            .andExpect(openApi().isValid(OpenApiContract.VALIDATOR));

        ArgumentCaptor<UpdateCourseSessionCommand> command =
            ArgumentCaptor.forClass(UpdateCourseSessionCommand.class);
        verify(service).update(command.capture(), eq(managerContext()));
        org.assertj.core.api.Assertions.assertThat(command.getValue().location())
            .matches(field -> field.present() && field.value() == null);
    }

    @Test
    void getsSessionAndMapsApplicationErrors() throws Exception {
        when(service.get(SESSION_ID)).thenReturn(session());
        mockMvc.perform(get("/api/v1/sessions/{sessionId}", SESSION_ID)
                .with(user("reader").roles("USER"))
                .header(RequestIdFilter.HEADER_NAME, REQUEST_ID))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("SCHEDULED"))
            .andExpect(openApi().isValid(OpenApiContract.VALIDATOR));

        when(service.get(SESSION_ID)).thenThrow(new ApiException(
            HttpStatus.NOT_FOUND, ApiErrorCode.COURSE_SESSION_NOT_FOUND,
            "Course session not found"));
        mockMvc.perform(get("/api/v1/sessions/{sessionId}", SESSION_ID)
                .with(user("reader").roles("USER")))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("COURSE_SESSION_NOT_FOUND"));
    }

    @Test
    void rejectsMalformedUuidInvalidBodyAndUnauthorizedMutation() throws Exception {
        mockMvc.perform(get("/api/v1/sessions/not-a-uuid")
                .with(user("reader").roles("USER")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
        mockMvc.perform(post("/api/v1/courses/{courseId}/sessions", COURSE_ID)
                .with(user("manager").roles("INSTITUTION_ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mockMvc.perform(post("/api/v1/courses/{courseId}/sessions", COURSE_ID)
                .with(user("learner").roles("LEARNER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(validCreateJson()))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mockMvc.perform(get("/api/v1/courses/{courseId}/sessions", COURSE_ID))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        verify(service, never()).create(any(), any());
    }

    private String validCreateJson() {
        return """
            {
              "sessionNo": 1,
              "startsAt": "2026-09-01T01:00:00Z",
              "endsAt": "2026-09-01T02:00:00Z",
              "location": "서울 교육장",
              "status": "SCHEDULED"
            }
            """;
    }

    private AuthenticatedUserContext managerContext() {
        return new AuthenticatedUserContext(
            UUID.fromString("11111111-1111-1111-1111-111111111111"),
            "LOCAL",
            Set.of(new AuthenticatedRole("INSTITUTION_ADMIN", INSTITUTION_ID))
        );
    }

    private CourseSession session() {
        Instant now = Instant.parse("2026-08-04T00:00:00Z");
        return new CourseSession(SESSION_ID, COURSE_ID, 1,
            Instant.parse("2026-09-01T01:00:00Z"),
            Instant.parse("2026-09-01T02:00:00Z"), "서울 교육장", null, null,
            CourseSessionStatus.SCHEDULED, now, now);
    }
}
