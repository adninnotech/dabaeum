package com.adn.dabaeum.user.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.adn.dabaeum.common.api.ApiExceptionHandler;
import com.adn.dabaeum.common.config.ClockConfiguration;
import com.adn.dabaeum.common.config.DevBearerSecurityConfiguration;
import com.adn.dabaeum.common.config.LocalDevSecuritySupportConfiguration;
import com.adn.dabaeum.common.config.JacksonConfiguration;
import com.adn.dabaeum.common.config.SecurityConfiguration;
import com.adn.dabaeum.common.security.AuthenticatedUserPrincipal;
import com.adn.dabaeum.common.web.RequestIdFilter;
import com.adn.dabaeum.user.application.UserApplicationService;
import com.adn.dabaeum.user.application.UserView;
import com.adn.dabaeum.user.domain.User;
import com.adn.dabaeum.user.domain.UserStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * 마이페이지의 이메일·SMS 알림 수신 설정.
 *
 * <p>보내지 않은 필드는 {@code null} 로 서비스에 넘어가야 한다. 원시 {@code boolean} 으로 받으면
 * "안 보냄" 이 {@code false} 로 읽혀 한쪽만 바꾼 요청이 나머지 한쪽을 조용히 꺼 버린다.
 */
@WebMvcTest(UserController.class)
@ActiveProfiles("local")
@TestPropertySource(properties =
    "dabaeum.security.dev.bearer-token=stage2b-task5-test-token")
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
class NotificationPreferenceTest {

    private static final UUID LEARNER = UUID.fromString(
        "77777777-7777-7777-7777-777777777777"
    );

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    UserApplicationService service;

    @Test
    void 알림_설정을_바꾼다() throws Exception {
        when(service.updateNotificationPreferences(any(), any(), any()))
            .thenReturn(preferences(false, true));

        mockMvc.perform(put("/api/v1/users/me/notification-preferences")
                .with(learner())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"emailNotificationEnabled": false, "smsNotificationEnabled": true}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.emailNotificationEnabled").value(false))
            .andExpect(jsonPath("$.data.smsNotificationEnabled").value(true));

        verify(service).updateNotificationPreferences(LEARNER, false, true);
    }

    @Test
    void 한쪽만_보내면_나머지는_null_로_넘어간다() throws Exception {
        when(service.updateNotificationPreferences(any(), any(), any()))
            .thenReturn(preferences(true, true));

        mockMvc.perform(put("/api/v1/users/me/notification-preferences")
                .with(learner())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"smsNotificationEnabled\": true}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.emailNotificationEnabled").value(true));

        ArgumentCaptor<Boolean> email = ArgumentCaptor.forClass(Boolean.class);
        ArgumentCaptor<Boolean> sms = ArgumentCaptor.forClass(Boolean.class);
        verify(service).updateNotificationPreferences(
            any(), email.capture(), sms.capture());
        Assertions.assertThat(email.getValue()).isNull();
        Assertions.assertThat(sms.getValue()).isTrue();
    }

    @Test
    void 마이페이지_조회에_설정이_포함된다() throws Exception {
        when(service.getMe(LEARNER))
            .thenReturn(new UserView(preferences(true, false), List.of()));

        mockMvc.perform(get("/api/v1/users/me").with(learner()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.emailNotificationEnabled").value(true))
            .andExpect(jsonPath("$.data.smsNotificationEnabled").value(false));
    }

    @Test
    void 로그인하지_않으면_401() throws Exception {
        mockMvc.perform(put("/api/v1/users/me/notification-preferences")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"smsNotificationEnabled\": true}"))
            .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    void 모르는_필드는_400() throws Exception {
        mockMvc.perform(put("/api/v1/users/me/notification-preferences")
                .with(learner())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"pushNotificationEnabled\": true}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
        verifyNoInteractions(service);
    }

    private RequestPostProcessor learner() {
        return authentication(new UsernamePasswordAuthenticationToken(
            new AuthenticatedUserPrincipal(LEARNER, Set.of("ROLE_USER")),
            "N/A",
            Set.of(new SimpleGrantedAuthority("ROLE_USER"))
        ));
    }

    private User preferences(boolean email, boolean sms) {
        Instant instant = Instant.parse("2026-09-17T00:00:00Z");
        return new User(
            LEARNER,
            "학습자",
            "learner@example.com",
            "010-0000-0000",
            LocalDate.of(1990, 1, 1),
            UserStatus.ACTIVE,
            null,
            instant,
            instant
        ).withNotificationPreferences(email, sms, instant);
    }
}
