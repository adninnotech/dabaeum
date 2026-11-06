package com.adn.dabaeum.accesslog.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.adn.dabaeum.accesslog.application.AdminAccessLogQueryService;
import com.adn.dabaeum.accesslog.domain.AccessLogAction;
import com.adn.dabaeum.accesslog.domain.AdminAccessLog;
import com.adn.dabaeum.accesslog.domain.AdminAccessLogEntry;
import com.adn.dabaeum.accesslog.domain.AdminAccessLogPage;
import com.adn.dabaeum.common.api.ApiExceptionHandler;
import com.adn.dabaeum.common.config.ClockConfiguration;
import com.adn.dabaeum.common.config.DevBearerSecurityConfiguration;
import com.adn.dabaeum.common.config.JacksonConfiguration;
import com.adn.dabaeum.common.config.LocalDevSecuritySupportConfiguration;
import com.adn.dabaeum.common.config.SecurityConfiguration;
import com.adn.dabaeum.common.web.RequestIdFilter;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** 설계서 §10.2 — 관리자 접속기록 조회. 플랫폼 관리자 전용, 조회만. */
@WebMvcTest(AdminAccessLogController.class)
@ActiveProfiles("local")
@TestPropertySource(properties = "dabaeum.security.dev.bearer-token=access-log-token")
@Import({
    ApiExceptionHandler.class,
    ClockConfiguration.class,
    SecurityConfiguration.class,
    DevBearerSecurityConfiguration.class,
    LocalDevSecuritySupportConfiguration.class,
    JacksonConfiguration.class,
    RequestIdFilter.class
})
class AdminAccessLogControllerTest {

    private static final Instant AT = Instant.parse("2026-09-28T01:02:03Z");

    @Autowired MockMvc mockMvc;
    @MockitoBean AdminAccessLogQueryService service;

    @Test
    void listsLogsForPlatformAdmin() throws Exception {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        AdminAccessLog log = new AdminAccessLog(
            UUID.randomUUID(), UUID.randomUUID(), "PLATFORM_ADMIN", AccessLogAction.READ,
            "GET /api/v1/users", List.of(a, b), 2, 200, "203.0.113.7", "junit", UUID.randomUUID(), AT);
        when(service.list(anyInt(), anyInt(), any(), any(), any(), any()))
            .thenReturn(new AdminAccessLogPage(List.of(new AdminAccessLogEntry(log, "관리자")), 21));

        mockMvc.perform(get("/api/v1/admin/access-logs")
                .param("page", "1").param("size", "10").param("action", "READ")
                .param("from", "2026-09-01").param("to", "2026-09-28")
                .with(user("admin").roles("PLATFORM_ADMIN")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].actorName").value("관리자"))
            .andExpect(jsonPath("$.data[0].targetUserIds.length()").value(2))
            .andExpect(jsonPath("$.page.totalElements").value(21))
            .andExpect(jsonPath("$.page.totalPages").value(3));
    }

    @Test
    void countOnlyRowHasNullTargets() throws Exception {
        AdminAccessLog log = new AdminAccessLog(
            UUID.randomUUID(), UUID.randomUUID(), "INSTITUTION_ADMIN", AccessLogAction.READ,
            "GET /api/v1/admin/inquiries", null, 7, 200, null, null, null, AT);
        when(service.list(eq(0), eq(20), any(), any(), any(), any()))
            .thenReturn(new AdminAccessLogPage(List.of(new AdminAccessLogEntry(log, null)), 1));

        mockMvc.perform(get("/api/v1/admin/access-logs").with(user("admin").roles("PLATFORM_ADMIN")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].targetUserIds").doesNotExist())
            .andExpect(jsonPath("$.data[0].resultCount").value(7));
    }

    @Test
    void learnerIsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/admin/access-logs").with(user("learner").roles("LEARNER")))
            .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
}
