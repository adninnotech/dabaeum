package com.adn.dabaeum.attendance.api;

import static com.atlassian.oai.validator.mockmvc.OpenApiValidationMatchers.openApi;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.adn.dabaeum.attendance.application.AttendanceQrTokenApplicationService;
import com.adn.dabaeum.attendance.application.AttendanceApplicationService;
import com.adn.dabaeum.attendance.application.IssuedAttendanceQrToken;
import com.adn.dabaeum.common.api.ApiExceptionHandler;
import com.adn.dabaeum.common.config.ClockConfiguration;
import com.adn.dabaeum.common.config.DevBearerSecurityConfiguration;
import com.adn.dabaeum.common.config.JacksonConfiguration;
import com.adn.dabaeum.common.config.SecurityConfiguration;
import com.adn.dabaeum.common.security.AuthenticatedRole;
import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import com.adn.dabaeum.common.security.CurrentUserProvider;
import com.adn.dabaeum.common.web.RequestIdFilter;
import com.adn.dabaeum.testsupport.OpenApiContract;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Set;
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

@WebMvcTest(AttendanceController.class)
@ActiveProfiles("local")
@TestPropertySource(properties = "dabaeum.security.dev.bearer-token=attendance-qr-test-token")
@Import({
    AttendanceApiMapper.class,
    ApiExceptionHandler.class,
    ClockConfiguration.class,
    SecurityConfiguration.class,
    DevBearerSecurityConfiguration.class,
    JacksonConfiguration.class,
    RequestIdFilter.class
})
class AttendanceQrTokenControllerTest {

    private static final UUID SESSION_ID = UUID.fromString(
        "22222222-2222-2222-2222-222222222222");
    private static final String REQUEST_ID = "9b706c0e-96cf-4d0a-b357-d80752facf1c";
    private static final Path CONTRACT =
        Path.of("docs/api/dabaeum-api-v1.yaml").toAbsolutePath();

    @Autowired MockMvc mockMvc;

    @MockitoBean AttendanceQrTokenApplicationService service;
    @MockitoBean AttendanceApplicationService attendanceApplicationService;
    @MockitoBean CurrentUserProvider currentUserProvider;

    @Test
    void issuesQrTokenForInstructorAndPreservesResponseEnvelope() throws Exception {
        AuthenticatedUserContext context = new AuthenticatedUserContext(
            UUID.fromString("11111111-1111-1111-1111-111111111111"), "LOCAL",
            Set.of(new AuthenticatedRole("INSTRUCTOR", UUID.fromString(
                "33333333-3333-3333-3333-333333333333"))));
        when(currentUserProvider.requireContext()).thenReturn(context);
        when(service.issue(SESSION_ID, context)).thenReturn(new IssuedAttendanceQrToken(
            "encoded-token", Instant.parse("2026-08-05T00:00:30Z")));

        mockMvc.perform(post("/api/v1/sessions/{sessionId}/qr-token", SESSION_ID)
                .with(user("instructor").roles("INSTRUCTOR"))
                .header(RequestIdFilter.HEADER_NAME, REQUEST_ID)
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isCreated())
            .andExpect(header().string(RequestIdFilter.HEADER_NAME, REQUEST_ID))
            .andExpect(jsonPath("$.data.token").value("encoded-token"))
            .andExpect(jsonPath("$.data.expiresAt").value("2026-08-05T00:00:30Z"))
            .andExpect(jsonPath("$.meta.requestId").value(REQUEST_ID))
            .andExpect(openApi().isValid(OpenApiContract.VALIDATOR));

        verify(service).issue(eq(SESSION_ID), eq(context));
    }

    @Test
    void rejectsLearnerAtSecurityBoundary() throws Exception {
        mockMvc.perform(post("/api/v1/sessions/{sessionId}/qr-token", SESSION_ID)
                .with(user("learner").roles("LEARNER")))
            .andExpect(status().isForbidden());
    }
}
