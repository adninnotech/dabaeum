package com.adn.dabaeum.attendance.api;

import static com.atlassian.oai.validator.mockmvc.OpenApiValidationMatchers.openApi;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.adn.dabaeum.attendance.application.AttendanceApplicationService;
import com.adn.dabaeum.attendance.application.AttendancePage;
import com.adn.dabaeum.attendance.application.AttendanceQrTokenApplicationService;
import com.adn.dabaeum.attendance.domain.Attendance;
import com.adn.dabaeum.attendance.domain.AttendanceMethod;
import com.adn.dabaeum.attendance.domain.AttendanceSource;
import com.adn.dabaeum.attendance.domain.AttendanceStatus;
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
import java.time.Instant;
import java.nio.file.Path;
import java.util.List;
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
@TestPropertySource(properties = "dabaeum.security.dev.bearer-token=attendance-task5-token")
@Import({
    AttendanceApiMapper.class,
    ApiExceptionHandler.class,
    ClockConfiguration.class,
    SecurityConfiguration.class,
    DevBearerSecurityConfiguration.class,
    JacksonConfiguration.class,
    RequestIdFilter.class
})
class AttendanceRecordQueryControllerTest {

    private static final Path CONTRACT =
        Path.of("docs/api/dabaeum-api-v1.yaml").toAbsolutePath();
    private static final UUID SESSION_ID = UUID.fromString(
        "22222222-2222-2222-2222-222222222222");
    private static final UUID ATTENDANCE_ID = UUID.fromString(
        "99999999-9999-9999-9999-999999999999");
    private static final String REQUEST_ID = "4f4b5d3f-1c29-48ae-94f6-4a66d9a1e2b1";

    @Autowired MockMvc mockMvc;
    @MockitoBean AttendanceApplicationService service;
    @MockitoBean AttendanceQrTokenApplicationService qrTokenService;
    @MockitoBean CurrentUserProvider currentUserProvider;

    @Test
    void recordsAttendanceWithCreatedLocationAndOpenApiContract() throws Exception {
        when(currentUserProvider.requireContext()).thenReturn(subject());
        when(service.record(any(), any())).thenReturn(attendance());

        mockMvc.perform(post("/api/v1/sessions/{sessionId}/attendance", SESSION_ID)
                .with(user("learner").roles("LEARNER"))
                .header(RequestIdFilter.HEADER_NAME, REQUEST_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(validQrJson()))
            .andExpect(status().isCreated())
            .andExpect(header().string("Location", "/api/v1/attendance/" + ATTENDANCE_ID))
            .andExpect(jsonPath("$.data.id").value(ATTENDANCE_ID.toString()))
            .andExpect(openApi().isValid(OpenApiContract.VALIDATOR));
        verify(service).record(any(), eq(subject()));
    }

    @Test
    void listsAndGetsAttendanceWithOpenApiEnvelope() throws Exception {
        when(currentUserProvider.requireContext()).thenReturn(subject());
        when(service.list(any(), any())).thenReturn(new AttendancePage(
            List.of(attendance()), 0, 20, 1, 1));
        when(service.get(ATTENDANCE_ID, subject())).thenReturn(attendance());

        mockMvc.perform(get("/api/v1/sessions/{sessionId}/attendance", SESSION_ID)
                .with(user("learner").roles("LEARNER"))
                .header(RequestIdFilter.HEADER_NAME, REQUEST_ID))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].status").value("PRESENT"))
            .andExpect(jsonPath("$.page.totalElements").value(1))
            .andExpect(openApi().isValid(OpenApiContract.VALIDATOR));

        mockMvc.perform(get("/api/v1/attendance/{attendanceId}", ATTENDANCE_ID)
                .with(user("learner").roles("LEARNER"))
                .header(RequestIdFilter.HEADER_NAME, REQUEST_ID))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.id").value(ATTENDANCE_ID.toString()))
            .andExpect(openApi().isValid(OpenApiContract.VALIDATOR));
    }

    @Test
    void rejectsUnknownPropertyAndLearnerIsNotAllowedToListBySecurityRole() throws Exception {
        mockMvc.perform(post("/api/v1/sessions/{sessionId}/attendance", SESSION_ID)
                .with(user("learner").roles("LEARNER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(validQrJson().replace("}", ",\"unexpected\":true}")))
            .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/sessions/{sessionId}/attendance", SESSION_ID))
            .andExpect(status().isUnauthorized());
    }

    private String validQrJson() {
        return """
            {
              "enrollmentId": "77777777-7777-7777-7777-777777777777",
              "attendanceMethod": "QR",
              "status": "PRESENT",
              "source": "APP",
              "qrToken": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
            }
            """;
    }

    private Attendance attendance() {
        Instant now = Instant.parse("2026-08-05T00:00:00Z");
        return new Attendance(ATTENDANCE_ID,
            UUID.fromString("33333333-3333-3333-3333-333333333333"), SESSION_ID,
            UUID.fromString("77777777-7777-7777-7777-777777777777"),
            UUID.fromString("88888888-8888-8888-8888-888888888888"),
            AttendanceMethod.QR, AttendanceStatus.PRESENT, now, AttendanceSource.APP,
            UUID.fromString("55555555-5555-5555-5555-555555555555"), now, now);
    }

    private AuthenticatedUserContext subject() {
        return new AuthenticatedUserContext(
            UUID.fromString("55555555-5555-5555-5555-555555555555"), "LOCAL",
            Set.of(new AuthenticatedRole("LEARNER", UUID.fromString(
                "44444444-4444-4444-4444-444444444444"))));
    }
}
