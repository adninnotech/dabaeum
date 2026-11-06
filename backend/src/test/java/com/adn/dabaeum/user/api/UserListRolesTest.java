package com.adn.dabaeum.user.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import com.adn.dabaeum.role.domain.RoleSummary;
import com.adn.dabaeum.role.domain.UserRole;
import com.adn.dabaeum.user.application.UserApplicationService;
import com.adn.dabaeum.user.application.UserPage;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * 회원 목록·본인 조회가 역할을 함께 준다. 프론트가 행마다 GET /users/{id}/roles 를 부르던
 * N+1 을 없애는 것이 목적이라, 역할이 없어도 빈 배열이어야 화면이 분기 없이 그릴 수 있다.
 */
@WebMvcTest(UserController.class)
@ActiveProfiles("local")
@TestPropertySource(properties =
    "dabaeum.security.dev.bearer-token=user-list-roles-test-token")
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
class UserListRolesTest {

    private static final UUID INSTRUCTOR_ID = UUID.fromString(
        "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1"
    );
    private static final UUID PLAIN_ID = UUID.fromString(
        "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa2"
    );
    private static final UUID INSTITUTION_ID = UUID.fromString(
        "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbb1"
    );

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    UserApplicationService service;

    @Test
    void 목록이_사용자마다_역할을_준다() throws Exception {
        when(service.list(any())).thenReturn(page(
            instructorView(),
            plainView()
        ));

        mockMvc.perform(get("/api/v1/users")
                .with(SecurityMockMvcRequestPostProcessors.user("platform-admin").roles("PLATFORM_ADMIN")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].roles").isArray())
            .andExpect(jsonPath("$.data[0].roles[0].role")
                .value("PLATFORM_ADMIN"));
    }

    @Test
    void 역할이_둘이면_둘_다_온다() throws Exception {
        when(service.list(any())).thenReturn(page(instructorView()));

        mockMvc.perform(get("/api/v1/users")
                .with(SecurityMockMvcRequestPostProcessors.user("platform-admin").roles("PLATFORM_ADMIN")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].roles.length()").value(2));
    }

    @Test
    void 기관_역할은_기관명도_준다() throws Exception {
        when(service.list(any())).thenReturn(page(instructorView()));

        mockMvc.perform(get("/api/v1/users")
                .with(SecurityMockMvcRequestPostProcessors.user("platform-admin").roles("PLATFORM_ADMIN")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].roles[1].role").value("INSTRUCTOR"))
            .andExpect(jsonPath("$.data[0].roles[1].institutionId")
                .value(INSTITUTION_ID.toString()))
            .andExpect(jsonPath("$.data[0].roles[1].institutionName")
                .value("테스트 평생교육원"))
            // 전역 역할은 기관이 없어 기관명도 비어 있다.
            .andExpect(jsonPath("$.data[0].roles[0].institutionName")
                .doesNotExist());
    }

    @Test
    void 역할이_없는_사용자는_빈_배열이다() throws Exception {
        when(service.list(any())).thenReturn(page(plainView()));

        mockMvc.perform(get("/api/v1/users")
                .with(SecurityMockMvcRequestPostProcessors.user("platform-admin").roles("PLATFORM_ADMIN")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].roles").isArray())
            .andExpect(jsonPath("$.data[0].roles").isEmpty());
    }

    @Test
    void 본인_조회도_역할을_준다() throws Exception {
        when(service.getMe(INSTRUCTOR_ID)).thenReturn(instructorView());

        mockMvc.perform(get("/api/v1/users/me").with(principal(INSTRUCTOR_ID)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.roles.length()").value(2))
            .andExpect(jsonPath("$.data.roles[1].institutionName")
                .value("테스트 평생교육원"));
    }

    private UserPage page(UserView... views) {
        List<UserView> data = List.of(views);
        return new UserPage(data, 0, 20, data.size(), 1);
    }

    private UserView instructorView() {
        return new UserView(
            user(INSTRUCTOR_ID, "강사 사용자"),
            List.of(
                new RoleSummary(UserRole.PLATFORM_ADMIN, null, null),
                new RoleSummary(
                    UserRole.INSTRUCTOR,
                    INSTITUTION_ID,
                    "테스트 평생교육원"
                )
            )
        );
    }

    private UserView plainView() {
        return new UserView(user(PLAIN_ID, "역할없음"), List.of());
    }

    private User user(UUID id, String name) {
        Instant instant = Instant.parse("2026-09-18T00:00:00Z");
        return new User(
            id,
            name,
            "user@example.com",
            "010-1234-5678",
            LocalDate.of(2000, 1, 2),
            UserStatus.ACTIVE,
            null,
            instant,
            instant
        );
    }

    private RequestPostProcessor principal(UUID userId) {
        return authentication(new UsernamePasswordAuthenticationToken(
            new AuthenticatedUserPrincipal(userId, Set.of("ROLE_USER")),
            "N/A",
            Set.of(new SimpleGrantedAuthority("ROLE_USER"))
        ));
    }
}
