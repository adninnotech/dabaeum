package com.adn.dabaeum.attendance.api;

import static com.atlassian.oai.validator.mockmvc.OpenApiValidationMatchers.openApi;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.adn.dabaeum.attendance.application.AttendanceApplicationService;
import com.adn.dabaeum.attendance.application.AttendanceMetrics;
import com.adn.dabaeum.attendance.domain.Attendance;
import com.adn.dabaeum.attendance.domain.AttendanceMethod;
import com.adn.dabaeum.attendance.domain.AttendanceSource;
import com.adn.dabaeum.attendance.domain.AttendanceStatus;
import com.adn.dabaeum.attendance.application.AttendanceQrTokenApplicationService;
import com.adn.dabaeum.common.api.ApiExceptionHandler;
import com.adn.dabaeum.common.config.ClockConfiguration;
import com.adn.dabaeum.common.config.JacksonConfiguration;
import com.adn.dabaeum.common.config.SecurityConfiguration;
import com.adn.dabaeum.common.security.AuthenticatedRole;
import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import com.adn.dabaeum.common.security.CurrentUserProvider;
import com.adn.dabaeum.common.web.RequestIdFilter;
import com.adn.dabaeum.testsupport.OpenApiContract;
import java.nio.file.Path;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AttendanceController.class)
@Import({AttendanceApiMapper.class, ApiExceptionHandler.class, ClockConfiguration.class,
    SecurityConfiguration.class, JacksonConfiguration.class, RequestIdFilter.class})
class AttendanceAdjustmentSummaryControllerTest {

    private static final Path CONTRACT = Path.of("docs/api/dabaeum-api-v1.yaml").toAbsolutePath();
    private static final UUID ATTENDANCE_ID = UUID.fromString(
        "11111111-1111-1111-1111-111111111111");
    private static final UUID ENROLLMENT_ID = UUID.fromString(
        "77777777-7777-7777-7777-777777777777");

    @Autowired MockMvc mockMvc;
    @MockitoBean AttendanceApplicationService service;
    @MockitoBean AttendanceQrTokenApplicationService qrTokenService;
    @MockitoBean CurrentUserProvider currentUserProvider;

    @Test
    void adjustsAndReadsSummaryWithOpenApiContracts() throws Exception {
        when(currentUserProvider.requireContext()).thenReturn(context());
        when(service.adjust(any(), any())).thenReturn(attendance());
        when(service.summary(ENROLLMENT_ID, context())).thenReturn(new AttendanceMetrics(
            ENROLLMENT_ID, 3, 1, 1, 1, 0, new BigDecimal("66.67"), 60));

        mockMvc.perform(patch("/api/v1/attendance/{attendanceId}", ATTENDANCE_ID)
                .with(user("manager").roles("INSTITUTION_ADMIN"))
                .contentType("application/json")
                .content("{\"status\":\"LATE\",\"reason\":\"현장 확인\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("PRESENT"))
            .andExpect(openApi().isValid(OpenApiContract.VALIDATOR));

        mockMvc.perform(get("/api/v1/enrollments/{enrollmentId}/attendance-summary",
                ENROLLMENT_ID).with(user("learner").roles("LEARNER")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.attendanceRate").value(66.67))
            .andExpect(jsonPath("$.data.totalSessions").value(3))
            .andExpect(openApi().isValid(OpenApiContract.VALIDATOR));
    }

    private AuthenticatedUserContext context() {
        return new AuthenticatedUserContext(
            UUID.fromString("66666666-6666-6666-6666-666666666666"), "LOCAL",
            Set.of(new AuthenticatedRole("INSTITUTION_ADMIN",
                UUID.fromString("44444444-4444-4444-4444-444444444444"))));
    }

    private Attendance attendance() {
        Instant now = Instant.parse("2026-08-05T00:00:00Z");
        return new Attendance(ATTENDANCE_ID,
            UUID.fromString("33333333-3333-3333-3333-333333333333"),
            UUID.fromString("22222222-2222-2222-2222-222222222222"), ENROLLMENT_ID, null,
            AttendanceMethod.ADMIN, AttendanceStatus.PRESENT, now, AttendanceSource.ADMIN_WEB,
            UUID.fromString("66666666-6666-6666-6666-666666666666"), now, now);
    }
}
