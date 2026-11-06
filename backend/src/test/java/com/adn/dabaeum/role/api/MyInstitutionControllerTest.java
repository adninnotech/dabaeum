package com.adn.dabaeum.role.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import com.adn.dabaeum.role.application.MyInstitution;
import com.adn.dabaeum.role.application.MyInstitutionApplicationService;
import com.adn.dabaeum.role.domain.UserRole;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(MyInstitutionController.class)
@ActiveProfiles("local")
@TestPropertySource(properties =
    "dabaeum.security.dev.bearer-token=my-institution-task7-test-token")
@Import({
    ApiExceptionHandler.class,
    ClockConfiguration.class,
    SecurityConfiguration.class,
    DevBearerSecurityConfiguration.class,
    LocalDevSecuritySupportConfiguration.class,
    JacksonConfiguration.class,
    RequestIdFilter.class
})
class MyInstitutionControllerTest {

    private static final UUID USER_ID = UUID.fromString(
        "11111111-1111-1111-1111-111111111111");
    private static final UUID INSTITUTION_ID = UUID.fromString(
        "22222222-2222-2222-2222-222222222222");

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    MyInstitutionApplicationService service;

    @MockitoBean
    CurrentUserProvider currentUserProvider;

    @Test
    void 내가_강사인_기관을_준다() throws Exception {
        when(currentUserProvider.requireContext()).thenReturn(instructor());
        when(service.listMine(any())).thenReturn(List.of(
            new MyInstitution(INSTITUTION_ID, "서울평생교육원", UserRole.INSTRUCTOR)));

        mockMvc.perform(get("/api/v1/users/me/institutions")
                .with(user("instructor").roles("INSTRUCTOR")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].institutionId").value(INSTITUTION_ID.toString()))
            .andExpect(jsonPath("$.data[0].name").value("서울평생교육원"))
            .andExpect(jsonPath("$.data[0].role").value("INSTRUCTOR"));
    }

    @Test
    void 소속이_없으면_빈_배열이다() throws Exception {
        when(currentUserProvider.requireContext()).thenReturn(learner());
        when(service.listMine(any())).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/users/me/institutions")
                .with(user("learner").roles("LEARNER")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data").isArray())
            .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void 기관이_없는_전역_역할은_빠진다() throws Exception {
        // PLATFORM_ADMIN 은 institutionId 가 null 이라 이 목록에 나오면 안 된다
        when(currentUserProvider.requireContext()).thenReturn(platformAdmin());
        when(service.listMine(any())).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/users/me/institutions")
                .with(user("platform-admin").roles("PLATFORM_ADMIN")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void 로그인하지_않으면_401() throws Exception {
        mockMvc.perform(get("/api/v1/users/me/institutions"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    private AuthenticatedUserContext instructor() {
        return new AuthenticatedUserContext(
            USER_ID, "LOCAL", Set.of(new AuthenticatedRole("INSTRUCTOR", INSTITUTION_ID)));
    }

    private AuthenticatedUserContext learner() {
        return new AuthenticatedUserContext(
            USER_ID, "LOCAL", Set.of(new AuthenticatedRole("LEARNER", null)));
    }

    private AuthenticatedUserContext platformAdmin() {
        return new AuthenticatedUserContext(
            USER_ID, "LOCAL", Set.of(new AuthenticatedRole("PLATFORM_ADMIN", null)));
    }
}
