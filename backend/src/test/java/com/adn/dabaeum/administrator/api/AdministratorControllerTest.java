package com.adn.dabaeum.administrator.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.adn.dabaeum.administrator.application.AdministratorService;
import com.adn.dabaeum.administrator.application.CreatedAdministrator;
import com.adn.dabaeum.administrator.domain.Administrator;
import com.adn.dabaeum.administrator.domain.AdministratorPage;
import com.adn.dabaeum.common.api.ApiExceptionHandler;
import com.adn.dabaeum.common.config.ClockConfiguration;
import com.adn.dabaeum.common.config.DevBearerSecurityConfiguration;
import com.adn.dabaeum.common.config.JacksonConfiguration;
import com.adn.dabaeum.common.config.LocalDevSecuritySupportConfiguration;
import com.adn.dabaeum.common.config.SecurityConfiguration;
import com.adn.dabaeum.common.web.RequestIdFilter;
import com.adn.dabaeum.role.domain.RoleSummary;
import com.adn.dabaeum.role.domain.UserRole;
import com.adn.dabaeum.user.domain.UserStatus;
import java.time.Instant;
import java.util.List;
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

/** 설계서 §10.1 — 관리자 목록·생성. 플랫폼 관리자 전용. */
@WebMvcTest(AdministratorController.class)
@ActiveProfiles("local")
@TestPropertySource(properties = "dabaeum.security.dev.bearer-token=administrator-token")
@Import({
    ApiExceptionHandler.class,
    ClockConfiguration.class,
    SecurityConfiguration.class,
    DevBearerSecurityConfiguration.class,
    LocalDevSecuritySupportConfiguration.class,
    JacksonConfiguration.class,
    RequestIdFilter.class
})
class AdministratorControllerTest {

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID INSTITUTION_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final Instant NOW = Instant.parse("2026-09-28T00:00:00Z");

    @Autowired MockMvc mockMvc;
    @MockitoBean AdministratorService service;

    @Test
    void listsAdministrators() throws Exception {
        when(service.list(anyInt(), anyInt(), any(), any(), any()))
            .thenReturn(new AdministratorPage(List.of(administrator()), 1));

        mockMvc.perform(get("/api/v1/admin/administrators").param("role", "INSTITUTION_ADMIN").param("q", "관리")
                .with(user("admin").roles("PLATFORM_ADMIN")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].userId").value(USER_ID.toString()))
            .andExpect(jsonPath("$.data[0].roles[0].role").value("INSTITUTION_ADMIN"))
            .andExpect(jsonPath("$.page.totalElements").value(1));
    }

    @Test
    void createsAdministratorAndShowsTemporaryPasswordOnce() throws Exception {
        when(service.create(any())).thenReturn(new CreatedAdministrator(administrator(), "Temp-Pass-2026!"));

        mockMvc.perform(post("/api/v1/admin/administrators")
                .with(user("admin").roles("PLATFORM_ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"기관 관리자","email":"admin@example.com","role":"INSTITUTION_ADMIN",
                     "institutionId":"%s"}
                    """.formatted(INSTITUTION_ID)))
            .andExpect(status().isCreated())
            .andExpect(header().string("Location", "http://localhost/api/v1/users/" + USER_ID))
            .andExpect(jsonPath("$.data.userId").value(USER_ID.toString()))
            .andExpect(jsonPath("$.data.temporaryPassword").value("Temp-Pass-2026!"))
            .andExpect(jsonPath("$.data.administrator.email").value("admin@example.com"));
    }

    @Test
    void unknownFieldIsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/admin/administrators")
                .with(user("admin").roles("PLATFORM_ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"a\",\"email\":\"a@example.com\",\"role\":\"PLATFORM_ADMIN\",\"password\":\"x\"}"))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void learnerIsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/admin/administrators").with(user("learner").roles("LEARNER")))
            .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/admin/administrators").with(user("learner").roles("LEARNER"))
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    private static Administrator administrator() {
        return new Administrator(USER_ID, "기관 관리자", "admin@example.com", UserStatus.ACTIVE,
            List.of(new RoleSummary(UserRole.INSTITUTION_ADMIN, INSTITUTION_ID, "대구과학대")),
            null, null, null, NOW);
    }
}
