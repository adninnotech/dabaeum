package com.adn.dabaeum.stage5;

import static com.atlassian.oai.validator.mockmvc.OpenApiValidationMatchers.openApi;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.adn.dabaeum.attendance.application.AttendanceApplicationService;
import com.adn.dabaeum.attendance.application.AttendanceMetrics;
import com.adn.dabaeum.attendance.application.AttendancePage;
import com.adn.dabaeum.attendance.application.AttendanceQrTokenApplicationService;
import com.adn.dabaeum.attendance.application.IssuedAttendanceQrToken;
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
import com.adn.dabaeum.completion.api.CompletionController;
import com.adn.dabaeum.completion.application.CompletionApplicationService;
import com.adn.dabaeum.completion.domain.Completion;
import com.adn.dabaeum.completion.domain.CompletionStatus;
import com.adn.dabaeum.completion.api.CompletionApiMapper;
import com.adn.dabaeum.attendance.api.AttendanceApiMapper;
import com.adn.dabaeum.attendance.api.AttendanceController;
import com.adn.dabaeum.testsupport.OpenApiContract;
import java.math.BigDecimal;
import java.nio.file.Path;
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

@WebMvcTest(controllers = {AttendanceController.class, CompletionController.class})
@ActiveProfiles("local")
@TestPropertySource(properties = "dabaeum.security.dev.bearer-token=stage5-acceptance-token")
@Import({
    AttendanceApiMapper.class,
    CompletionApiMapper.class,
    ApiExceptionHandler.class,
    ClockConfiguration.class,
    SecurityConfiguration.class,
    DevBearerSecurityConfiguration.class,
    JacksonConfiguration.class,
    RequestIdFilter.class
})
class Stage5AttendanceCompletionAcceptanceTest {

    private static final Path CONTRACT =
        Path.of("docs/api/dabaeum-api-v1.yaml").toAbsolutePath();
    private static final UUID SESSION_ID = UUID.fromString(
        "22222222-2222-2222-2222-222222222222");
    private static final UUID ATTENDANCE_ID = UUID.fromString(
        "11111111-1111-1111-1111-111111111111");
    private static final UUID ENROLLMENT_ID = UUID.fromString(
        "33333333-3333-3333-3333-333333333333");

    @Autowired MockMvc mockMvc;
    @MockitoBean AttendanceQrTokenApplicationService qrService;
    @MockitoBean AttendanceApplicationService attendanceService;
    @MockitoBean CompletionApplicationService completionService;
    @MockitoBean CurrentUserProvider currentUserProvider;

    @Test
    void acceptsAllNineStage5OperationEnvelopes() throws Exception {
        when(currentUserProvider.requireContext()).thenReturn(manager());
        when(qrService.issue(any(), any())).thenReturn(new IssuedAttendanceQrToken(
            "sample-qr-token-redacted", Instant.parse("2026-08-05T00:00:30Z")));
        when(attendanceService.list(any(), any())).thenReturn(
            new AttendancePage(List.of(attendance()), 0, 20, 1, 1));
        when(attendanceService.record(any(), any())).thenReturn(attendance());
        when(attendanceService.get(any(), any())).thenReturn(attendance());
        when(attendanceService.adjust(any(), any())).thenReturn(attendance());
        when(attendanceService.summary(any(), any())).thenReturn(new AttendanceMetrics(
            ENROLLMENT_ID, 3, 1, 1, 1, 0, new BigDecimal("66.67"), 120));
        when(completionService.get(any(), any())).thenReturn(completion(CompletionStatus.ELIGIBLE));
        when(completionService.evaluate(any(), any())).thenReturn(
            completion(CompletionStatus.ELIGIBLE));
        when(completionService.confirm(any(), any())).thenReturn(
            completion(CompletionStatus.COMPLETED));

        mockMvc.perform(post("/api/v1/sessions/{id}/qr-token", SESSION_ID)
                .with(user("manager").roles("INSTITUTION_ADMIN")))
            .andExpect(status().isCreated()).andExpect(openApi().isValid(OpenApiContract.VALIDATOR));
        mockMvc.perform(get("/api/v1/sessions/{id}/attendance", SESSION_ID)
                .with(user("manager").roles("INSTITUTION_ADMIN")))
            .andExpect(status().isOk()).andExpect(openApi().isValid(OpenApiContract.VALIDATOR));
        mockMvc.perform(post("/api/v1/sessions/{id}/attendance", SESSION_ID)
                .with(user("manager").roles("INSTITUTION_ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"enrollmentId":"33333333-3333-3333-3333-333333333333",
                     "attendanceMethod":"ADMIN","status":"PRESENT","source":"ADMIN_WEB"}
                    """))
            .andExpect(status().isCreated()).andExpect(openApi().isValid(OpenApiContract.VALIDATOR));
        mockMvc.perform(get("/api/v1/attendance/{id}", ATTENDANCE_ID)
                .with(user("manager").roles("INSTITUTION_ADMIN")))
            .andExpect(status().isOk()).andExpect(openApi().isValid(OpenApiContract.VALIDATOR));
        mockMvc.perform(patch("/api/v1/attendance/{id}", ATTENDANCE_ID)
                .with(user("manager").roles("INSTITUTION_ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"LATE\",\"reason\":\"현장 확인\"}"))
            .andExpect(status().isOk()).andExpect(openApi().isValid(OpenApiContract.VALIDATOR));
        mockMvc.perform(get("/api/v1/enrollments/{id}/attendance-summary", ENROLLMENT_ID)
                .with(user("manager").roles("INSTITUTION_ADMIN")))
            .andExpect(status().isOk()).andExpect(openApi().isValid(OpenApiContract.VALIDATOR));
        mockMvc.perform(get("/api/v1/enrollments/{id}/completion", ENROLLMENT_ID)
                .with(user("manager").roles("INSTITUTION_ADMIN")))
            .andExpect(status().isOk()).andExpect(openApi().isValid(OpenApiContract.VALIDATOR));
        mockMvc.perform(post("/api/v1/enrollments/{id}/completion/evaluate", ENROLLMENT_ID)
                .with(user("manager").roles("INSTITUTION_ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"attendanceRate\":66.67,\"completedMinutes\":120,\"creditValue\":null}"))
            .andExpect(status().isOk()).andExpect(openApi().isValid(OpenApiContract.VALIDATOR));
        mockMvc.perform(post("/api/v1/enrollments/{id}/completion/confirm", ENROLLMENT_ID)
                .with(user("manager").roles("INSTITUTION_ADMIN")))
            .andExpect(status().isOk()).andExpect(openApi().isValid(OpenApiContract.VALIDATOR));
    }

    private Attendance attendance() {
        Instant now = Instant.parse("2026-08-05T00:00:00Z");
        return new Attendance(ATTENDANCE_ID,
            UUID.fromString("44444444-4444-4444-4444-444444444444"), SESSION_ID,
            ENROLLMENT_ID, null, AttendanceMethod.ADMIN, AttendanceStatus.PRESENT, now,
            AttendanceSource.ADMIN_WEB, manager().userId(), now, now);
    }

    private Completion completion(CompletionStatus status) {
        Instant now = Instant.parse("2026-08-05T00:00:00Z");
        return status == CompletionStatus.ELIGIBLE
            ? new Completion(UUID.randomUUID(), ENROLLMENT_ID, status, new BigDecimal("66.67"),
                120, null, now, null, null, null, null, now, now)
            : new Completion(UUID.randomUUID(), ENROLLMENT_ID, status, new BigDecimal("66.67"),
                120, null, now, now, manager().userId(), now, null, now, now);
    }

    private AuthenticatedUserContext manager() {
        return new AuthenticatedUserContext(UUID.fromString(
            "55555555-5555-5555-5555-555555555555"), "LOCAL",
            Set.of(new AuthenticatedRole("INSTITUTION_ADMIN", UUID.fromString(
                "66666666-6666-6666-6666-666666666666"))));
    }
}
