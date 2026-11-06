package com.adn.dabaeum.attendance.api;

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
import com.adn.dabaeum.attendance.application.AttendanceDetail;
import com.adn.dabaeum.attendance.application.AttendancePage;
import com.adn.dabaeum.attendance.application.AttendanceQrTokenApplicationService;
import com.adn.dabaeum.attendance.domain.Attendance;
import com.adn.dabaeum.attendance.domain.AttendanceAdjustmentEntry;
import com.adn.dabaeum.attendance.domain.AttendanceMethod;
import com.adn.dabaeum.attendance.domain.AttendanceSource;
import com.adn.dabaeum.attendance.domain.AttendanceStatus;
import com.adn.dabaeum.common.api.ApiExceptionHandler;
import com.adn.dabaeum.common.config.ClockConfiguration;
import com.adn.dabaeum.common.config.DevBearerSecurityConfiguration;
import com.adn.dabaeum.common.config.LocalDevSecuritySupportConfiguration;
import com.adn.dabaeum.common.config.JacksonConfiguration;
import com.adn.dabaeum.common.config.SecurityConfiguration;
import com.adn.dabaeum.common.security.AuthenticatedRole;
import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import com.adn.dabaeum.common.security.CurrentUserProvider;
import com.adn.dabaeum.common.web.RequestIdFilter;
import java.time.Instant;
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
    LocalDevSecuritySupportConfiguration.class,
    JacksonConfiguration.class,
    RequestIdFilter.class
})
class AttendanceRecordQueryControllerTest {

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
                .with(user("instructor").roles("INSTRUCTOR"))
                .header(RequestIdFilter.HEADER_NAME, REQUEST_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(validAdminJson()))
            .andExpect(status().isCreated())
            .andExpect(header().string("Location", "/api/v1/attendance/" + ATTENDANCE_ID))
            .andExpect(jsonPath("$.data.id").value(ATTENDANCE_ID.toString()));
        verify(service).record(any(), eq(subject()));
    }

    @Test
    void listsAndGetsAttendanceWithOpenApiEnvelope() throws Exception {
        when(currentUserProvider.requireContext()).thenReturn(subject());
        Attendance listed = attendance();
        UUID learnerId = UUID.fromString("99999999-9999-9999-9999-999999999999");
        when(service.list(any(), any())).thenReturn(new AttendancePage(
            List.of(listed), 0, 20, 1, 1,
            java.util.Map.of(listed.enrollmentId(), new com.adn.dabaeum.attendance.domain
                .AttendanceLearner(listed.enrollmentId(), learnerId, "한상희"))));
        UUID adjusterId = UUID.fromString("88888888-8888-8888-8888-888888888888");
        when(service.get(ATTENDANCE_ID, subject())).thenReturn(new AttendanceDetail(
            attendance(), List.of(
                new AttendanceAdjustmentEntry(
                    UUID.fromString("77777777-7777-7777-7777-777777777778"),
                    AttendanceStatus.LATE, AttendanceStatus.PRESENT, "재확인",
                    adjusterId, "김강사", Instant.parse("2026-08-05T02:00:00Z")),
                new AttendanceAdjustmentEntry(
                    UUID.fromString("77777777-7777-7777-7777-777777777777"),
                    AttendanceStatus.PRESENT, AttendanceStatus.LATE, "현장 확인",
                    adjusterId, null, Instant.parse("2026-08-05T01:00:00Z")))));

        mockMvc.perform(get("/api/v1/sessions/{sessionId}/attendance", SESSION_ID)
                .with(user("learner").roles("LEARNER"))
                .header(RequestIdFilter.HEADER_NAME, REQUEST_ID))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].status").value("PRESENT"))
            .andExpect(jsonPath("$.data[0].userName").value("한상희"))
            .andExpect(jsonPath("$.data[0].userId").value(learnerId.toString()))
            .andExpect(jsonPath("$.page.totalElements").value(1));

        mockMvc.perform(get("/api/v1/attendance/{attendanceId}", ATTENDANCE_ID)
                .with(user("learner").roles("LEARNER"))
                .header(RequestIdFilter.HEADER_NAME, REQUEST_ID))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.id").value(ATTENDANCE_ID.toString()))
            .andExpect(jsonPath("$.data.userName").doesNotExist())
            .andExpect(jsonPath("$.data.adjustments.length()").value(2))
            .andExpect(jsonPath("$.data.adjustments[0].fromStatus").value("LATE"))
            .andExpect(jsonPath("$.data.adjustments[0].toStatus").value("PRESENT"))
            .andExpect(jsonPath("$.data.adjustments[0].reason").value("재확인"))
            .andExpect(jsonPath("$.data.adjustments[0].adjustedBy").value(adjusterId.toString()))
            .andExpect(jsonPath("$.data.adjustments[0].adjustedByName").value("김강사"))
            .andExpect(jsonPath("$.data.adjustments[0].adjustedAt").value("2026-08-05T02:00:00Z"))
            .andExpect(jsonPath("$.data.adjustments[1].adjustedByName")
                .value(org.hamcrest.Matchers.nullValue()));

        mockMvc.perform(get("/api/v1/sessions/{sessionId}/attendance", SESSION_ID)
                .with(user("learner").roles("LEARNER"))
                .header(RequestIdFilter.HEADER_NAME, REQUEST_ID))
            .andExpect(jsonPath("$.data[0].adjustments").doesNotExist());
    }

    @Test
    void rejectsUnknownPropertyAndAnonymousListing() throws Exception {
        mockMvc.perform(post("/api/v1/sessions/{sessionId}/attendance", SESSION_ID)
                .with(user("instructor").roles("INSTRUCTOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(validAdminJson().replace("}", ",\"unexpected\":true}")))
            .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/sessions/{sessionId}/attendance", SESSION_ID))
            .andExpect(status().isUnauthorized());
    }

    private String validAdminJson() {
        return """
            {
              "enrollmentId": "77777777-7777-7777-7777-777777777777",
              "attendanceMethod": "ADMIN",
              "status": "PRESENT",
              "source": "ADMIN_WEB"
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
