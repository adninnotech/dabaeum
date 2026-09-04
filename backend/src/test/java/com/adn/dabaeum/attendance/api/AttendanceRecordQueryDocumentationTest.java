package com.adn.dabaeum.attendance.api;

import static com.atlassian.oai.validator.mockmvc.OpenApiValidationMatchers.openApi;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessRequest;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
import com.adn.dabaeum.common.config.JacksonConfiguration;
import com.adn.dabaeum.common.config.SecurityConfiguration;
import com.adn.dabaeum.common.security.AuthenticatedRole;
import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import com.adn.dabaeum.common.security.CurrentUserProvider;
import com.adn.dabaeum.common.web.RequestIdFilter;
import com.adn.dabaeum.testsupport.OpenApiContract;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.restdocs.test.autoconfigure.AutoConfigureRestDocs;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AttendanceController.class)
@AutoConfigureRestDocs(uriScheme = "https", uriHost = "api.dabaeum.local", uriPort = 443)
@Import({
    AttendanceApiMapper.class,
    ApiExceptionHandler.class,
    ClockConfiguration.class,
    SecurityConfiguration.class,
    JacksonConfiguration.class,
    RequestIdFilter.class
})
class AttendanceRecordQueryDocumentationTest {

    private static final Path CONTRACT = Path.of("docs/api/dabaeum-api-v1.yaml").toAbsolutePath();
    private static final UUID SESSION_ID = UUID.fromString(
        "22222222-2222-2222-2222-222222222222");
    private static final UUID ATTENDANCE_ID = UUID.fromString(
        "99999999-9999-9999-9999-999999999999");

    @Autowired MockMvc mockMvc;
    @MockitoBean AttendanceApplicationService service;
    @MockitoBean AttendanceQrTokenApplicationService qrTokenService;
    @MockitoBean CurrentUserProvider currentUserProvider;

    @Test
    void documentsAttendanceRecordListAndGet() throws Exception {
        when(currentUserProvider.requireContext()).thenReturn(context());
        when(service.record(any(), any())).thenReturn(attendance());
        when(service.list(any(), any())).thenReturn(new AttendancePage(
            List.of(attendance()), 0, 20, 1, 1));
        when(service.get(ATTENDANCE_ID, context())).thenReturn(attendance());

        mockMvc.perform(post("/api/v1/sessions/{sessionId}/attendance", SESSION_ID)
                .with(user("learner").roles("LEARNER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "enrollmentId": "77777777-7777-7777-7777-777777777777",
                      "attendanceMethod": "QR",
                      "status": "PRESENT",
                      "source": "APP",
                      "qrToken": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(header().string("Location", "/api/v1/attendance/" + ATTENDANCE_ID))
            .andExpect(openApi().isValid(OpenApiContract.VALIDATOR))
            .andDo(document("attendance-record", preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint())));

        mockMvc.perform(get("/api/v1/sessions/{sessionId}/attendance", SESSION_ID)
                .with(user("learner").roles("LEARNER"))
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(openApi().isValid(OpenApiContract.VALIDATOR))
            .andDo(document("attendance-list", preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint())));

        mockMvc.perform(get("/api/v1/attendance/{attendanceId}", ATTENDANCE_ID)
                .with(user("learner").roles("LEARNER"))
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(openApi().isValid(OpenApiContract.VALIDATOR))
            .andDo(document("attendance-get", preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint())));
    }

    private AuthenticatedUserContext context() {
        return new AuthenticatedUserContext(
            UUID.fromString("55555555-5555-5555-5555-555555555555"), "LOCAL",
            Set.of(new AuthenticatedRole("LEARNER", UUID.fromString(
                "44444444-4444-4444-4444-444444444444"))));
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
}
