package com.adn.dabaeum.attendance.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
import org.hamcrest.Matchers;
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
 * 학생 폰이 QR 로 여는 페이지. 로그인하지 않은 요청이 200 을 받아야 한다 —
 * 본인 확인은 다대구 DID 가, 현장 확인은 QR 토큰이 한다.
 */
@WebMvcTest(DadaeguAttendController.class)
@ActiveProfiles("local")
@TestPropertySource(properties = "dabaeum.security.dev.bearer-token=attend-page-test-token")
@Import({
    ApiExceptionHandler.class,
    ClockConfiguration.class,
    SecurityConfiguration.class,
    DevBearerSecurityConfiguration.class,
    LocalDevSecuritySupportConfiguration.class,
    JacksonConfiguration.class,
    RequestIdFilter.class
})
class DadaeguAttendControllerTest {

    private static final UUID ATTEND_ID = UUID.fromString(
        "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final String RAW_TOKEN = "a".repeat(32);

    @Autowired MockMvc mockMvc;

    @MockitoBean DadaeguAttendSessionService service;
    @MockitoBean CurrentUserProvider currentUserProvider;

    @Test
    void servesStudentPageWithoutAuthenticationAndEmbedsDeepLink() throws Exception {
        when(service.open(RAW_TOKEN)).thenReturn(new AttendSessionView(
            ATTEND_ID, AttendState.WAITING, null, null, "didapp://x"));

        mockMvc.perform(get("/attend").param("t", RAW_TOKEN))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
            .andExpect(content().string(Matchers.containsString("didapp://x")))
            .andExpect(content().string(
                Matchers.containsString("/attend/" + ATTEND_ID + "/status")));
    }

    @Test
    void showsErrorPageWhenTokenIsRejected() throws Exception {
        when(service.open(any())).thenThrow(new ApiException(
            HttpStatus.CONFLICT, ApiErrorCode.ATTENDANCE_QR_EXPIRED, "expired"));

        mockMvc.perform(get("/attend").param("t", RAW_TOKEN))
            .andExpect(status().isConflict())
            .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
            .andExpect(content().string(Matchers.containsString("ATTENDANCE_QR_EXPIRED")));
    }

    @Test
    void returnsStatusJsonWithoutAuthentication() throws Exception {
        when(service.status(ATTEND_ID)).thenReturn(Optional.of(new AttendSessionView(
            ATTEND_ID, AttendState.RECORDED, "한상희", null, "didapp://x")));

        mockMvc.perform(get("/attend/{id}/status", ATTEND_ID))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.state").value("RECORDED"))
            .andExpect(jsonPath("$.studentName").value("한상희"));
    }

    @Test
    void returns404ForUnknownSession() throws Exception {
        when(service.status(any())).thenReturn(Optional.empty());

        mockMvc.perform(get("/attend/{id}/status", UUID.randomUUID()))
            .andExpect(status().isNotFound());
    }

    /** 다대구 공식 didLogin.js 처럼 앱에서 돌아온 순간에 묻는다. 2초마다 묻는 코드는 없어야 한다. */
    @Test
    void asksForStatusWhenStudentReturnsNotOnATimer() {
        String page = AttendPage.render(ATTEND_ID, "didapp://x");

        assertThat(page).contains("visibilitychange").contains("pageshow");
        assertThat(page).doesNotContain("setTimeout(poll, 2000)");
    }

    /** 딥링크는 다대구가 준 값이다. 그대로 붙이면 페이지가 조작될 수 있다. */
    @Test
    void escapesDeepLinkSoThePageCannotBeInjected() {
        String page = AttendPage.render(ATTEND_ID, "didapp://x?a=\"><script>alert(1)</script>");

        assertThat(page).doesNotContain("<script>alert(1)</script>");
    }

    @Test
    void missingTokenIsABadRequestPageNotAServerError() throws Exception {
        mockMvc.perform(get("/attend"))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML));
    }
}
