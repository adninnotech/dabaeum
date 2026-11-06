package com.adn.dabaeum.attendance.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.adn.dabaeum.attendance.application.AttendSessionView;
import com.adn.dabaeum.attendance.application.AttendState;
import com.adn.dabaeum.attendance.application.DadaeguAttendSessionService;
import com.adn.dabaeum.common.api.ApiErrorCode;
import com.adn.dabaeum.common.api.ApiException;
import com.adn.dabaeum.common.api.ApiExceptionHandler;
import com.adn.dabaeum.common.config.ClockConfiguration;
import com.adn.dabaeum.common.config.DevBearerSecurityConfiguration;
import com.adn.dabaeum.common.config.LocalDevSecuritySupportConfiguration;
import com.adn.dabaeum.common.config.JacksonConfiguration;
import com.adn.dabaeum.common.config.SecurityConfiguration;
import com.adn.dabaeum.common.security.CurrentUserProvider;
import com.adn.dabaeum.common.web.RequestIdFilter;
import java.util.Optional;
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

/**
 * 프론트 학생 출석 페이지가 부르는 JSON API. 학생은 로그인하지 않으므로 토큰 없이 불려야 한다 —
 * 본인 확인은 다대구 DID 가, 현장 확인은 QR 토큰이 한다.
 */
@WebMvcTest(DadaeguAttendSessionController.class)
@ActiveProfiles("local")
@TestPropertySource(properties = "dabaeum.security.dev.bearer-token=attend-session-test-token")
@Import({
    ApiExceptionHandler.class,
    ClockConfiguration.class,
    SecurityConfiguration.class,
    DevBearerSecurityConfiguration.class,
    LocalDevSecuritySupportConfiguration.class,
    JacksonConfiguration.class,
    RequestIdFilter.class
})
class DadaeguAttendSessionControllerTest {

    private static final UUID ATTEND_ID = UUID.fromString(
        "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final String RAW_TOKEN = "a".repeat(32);

    @Autowired MockMvc mockMvc;

    @MockitoBean DadaeguAttendSessionService service;
    @MockitoBean CurrentUserProvider currentUserProvider;

    @Test
    void opensSessionWithoutAuthenticationAndReturnsDeepLink() throws Exception {
        when(service.open(RAW_TOKEN)).thenReturn(new AttendSessionView(
            ATTEND_ID, AttendState.WAITING, null, null, "didapp://mobileweblogin?sessionKey=x"));

        mockMvc.perform(post("/api/v1/attend-sessions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + RAW_TOKEN + "\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.attendId").value(ATTEND_ID.toString()))
            .andExpect(jsonPath("$.data.state").value("WAITING"))
            .andExpect(jsonPath("$.data.deepLink").value("didapp://mobileweblogin?sessionKey=x"))
            .andExpect(jsonPath("$.meta.requestId").exists());
    }

    @Test
    void returnsConflictWhenQrTokenIsRejected() throws Exception {
        when(service.open(any())).thenThrow(new ApiException(
            HttpStatus.CONFLICT, ApiErrorCode.ATTENDANCE_QR_EXPIRED, "expired"));

        mockMvc.perform(post("/api/v1/attend-sessions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + RAW_TOKEN + "\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("ATTENDANCE_QR_EXPIRED"));
    }

    @Test
    void returnsServiceUnavailableWhenDadaeguIsDown() throws Exception {
        when(service.open(any())).thenThrow(new ApiException(
            HttpStatus.SERVICE_UNAVAILABLE, ApiErrorCode.INTERNAL_SERVER_ERROR,
            "Dadaegu is unavailable"));

        mockMvc.perform(post("/api/v1/attend-sessions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + RAW_TOKEN + "\"}"))
            .andExpect(status().isServiceUnavailable());
    }

    @Test
    void rejectsMissingTokenBeforeCallingDadaegu() throws Exception {
        mockMvc.perform(post("/api/v1/attend-sessions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isUnprocessableContent())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        verifyNoInteractions(service);
    }

    @Test
    void rejectsTooShortTokenBeforeCallingDadaegu() throws Exception {
        mockMvc.perform(post("/api/v1/attend-sessions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"short\"}"))
            .andExpect(status().isUnprocessableContent());

        verifyNoInteractions(service);
    }

    /** 결과 조회는 딥링크를 다시 내주지 않는다. 폰 화면에 필요한 상태만 싣는다. */
    @Test
    void returnsRecordedStateWithoutDeepLink() throws Exception {
        when(service.status(ATTEND_ID)).thenReturn(Optional.of(new AttendSessionView(
            ATTEND_ID, AttendState.RECORDED, "한상희", null, "didapp://x")));

        mockMvc.perform(get("/api/v1/attend-sessions/{attendId}", ATTEND_ID))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.state").value("RECORDED"))
            .andExpect(jsonPath("$.data.studentName").value("한상희"))
            .andExpect(jsonPath("$.data.deepLink").doesNotExist())
            .andExpect(jsonPath("$.data.errorCode").doesNotExist());
    }

    @Test
    void returnsFailedStateWithErrorCode() throws Exception {
        when(service.status(ATTEND_ID)).thenReturn(Optional.of(new AttendSessionView(
            ATTEND_ID, AttendState.FAILED, "한상희", "ATTENDANCE_CONFLICT", "didapp://x")));

        mockMvc.perform(get("/api/v1/attend-sessions/{attendId}", ATTEND_ID))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.state").value("FAILED"))
            .andExpect(jsonPath("$.data.errorCode").value("ATTENDANCE_CONFLICT"));
    }

    @Test
    void returnsNotFoundForUnknownSession() throws Exception {
        when(service.status(any())).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/attend-sessions/{attendId}", UUID.randomUUID()))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("ATTEND_SESSION_NOT_FOUND"));
    }
}
