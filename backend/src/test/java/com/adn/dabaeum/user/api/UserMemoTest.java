package com.adn.dabaeum.user.api;

import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * B-4 회원 상세의 연필 아이콘 — 사이트 전역 관리자 메모.
 * 마지막 테스트가 핵심이다: 관리자가 쓴 메모를 당사자가 GET /users/me 로 보면 사고다.
 */
@WebMvcTest(UserController.class)
@ActiveProfiles("local")
@TestPropertySource(properties =
    "dabaeum.security.dev.bearer-token=user-memo-test-token")
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
class UserMemoTest {

    private static final UUID USER_ID = UUID.fromString(
        "cccccccc-cccc-cccc-cccc-ccccccccccc1"
    );

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    UserApplicationService service;

    @Test
    void 메모를_남긴다() throws Exception {
        when(service.updateAdminMemo(USER_ID, "전화 문의 이력 있음"))
            .thenReturn(user("전화 문의 이력 있음"));
        lenient().when(service.rolesOf(USER_ID)).thenReturn(List.of());

        mockMvc.perform(patch("/api/v1/users/{userId}/memo", USER_ID)
                .with(admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"memo\": \"전화 문의 이력 있음\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.adminMemo").value("전화 문의 이력 있음"));
    }

    @Test
    void null_을_보내면_메모가_지워진다() throws Exception {
        when(service.updateAdminMemo(USER_ID, null)).thenReturn(user(null));
        lenient().when(service.rolesOf(USER_ID)).thenReturn(List.of());

        mockMvc.perform(patch("/api/v1/users/{userId}/memo", USER_ID)
                .with(admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"memo\": null}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.adminMemo").doesNotExist());
    }

    @Test
    void 빈_문자열은_422() throws Exception {
        mockMvc.perform(patch("/api/v1/users/{userId}/memo", USER_ID)
                .with(admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"memo\": \"\"}"))
            .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void 천자를_넘으면_422() throws Exception {
        mockMvc.perform(patch("/api/v1/users/{userId}/memo", USER_ID)
                .with(admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"memo\": \"" + "가".repeat(1001) + "\"}"))
            .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void 학습자는_메모를_남길_수_없다() throws Exception {
        mockMvc.perform(patch("/api/v1/users/{userId}/memo", USER_ID)
                .with(SecurityMockMvcRequestPostProcessors.user("learner").roles("LEARNER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"memo\": \"내가 쓴 메모\"}"))
            .andExpect(status().isForbidden());
    }

    @Test
    void 메모는_본인_조회에_나오지_않는다() throws Exception {
        // 관리자 메모를 당사자가 보면 사고다. /users/me 응답에는 절대 나오면 안 된다.
        when(service.getMe(USER_ID)).thenReturn(
            new UserView(user("관리자만 보는 메모"), List.of())
        );

        mockMvc.perform(get("/api/v1/users/me").with(principal(USER_ID)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.adminMemo").doesNotExist());
    }

    @Test
    void toResponse_는_includeAdminMemo_가_false면_메모를_숨긴다() {
        UserApiMapper mapper = new UserApiMapper(java.time.Clock.systemUTC());
        User withMemo = user("관리자만 보는 메모");

        UserResponse hidden = mapper.toResponse(withMemo, List.of(), false);
        UserResponse shown = mapper.toResponse(withMemo, List.of(), true);

        org.assertj.core.api.Assertions.assertThat(hidden.adminMemo()).isNull();
        org.assertj.core.api.Assertions.assertThat(shown.adminMemo())
            .isEqualTo("관리자만 보는 메모");
    }

    private User user(String adminMemo) {
        Instant instant = Instant.parse("2026-09-18T00:00:00Z");
        return new User(
            USER_ID,
            "메모 대상",
            "user@example.com",
            "010-1234-5678",
            LocalDate.of(2000, 1, 2),
            UserStatus.ACTIVE,
            null,
            instant,
            instant,
            null,
            null,
            null,
            null,
            null,
            User.DEFAULT_EMAIL_NOTIFICATION_ENABLED,
            User.DEFAULT_SMS_NOTIFICATION_ENABLED,
            adminMemo
        );
    }

    private RequestPostProcessor admin() {
        return SecurityMockMvcRequestPostProcessors.user("platform-admin").roles("PLATFORM_ADMIN");
    }

    private RequestPostProcessor principal(UUID userId) {
        return authentication(new UsernamePasswordAuthenticationToken(
            new AuthenticatedUserPrincipal(userId, Set.of("ROLE_USER")),
            "N/A",
            Set.of(new SimpleGrantedAuthority("ROLE_USER"))
        ));
    }
}
