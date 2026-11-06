package com.adn.dabaeum.usage.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.adn.dabaeum.common.api.ApiExceptionHandler;
import com.adn.dabaeum.common.config.ClockConfiguration;
import com.adn.dabaeum.common.config.DevBearerSecurityConfiguration;
import com.adn.dabaeum.common.config.JacksonConfiguration;
import com.adn.dabaeum.common.config.LocalDevSecuritySupportConfiguration;
import com.adn.dabaeum.common.config.SecurityConfiguration;
import com.adn.dabaeum.common.web.RequestIdFilter;
import com.adn.dabaeum.usage.application.UsageEventService;
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

/** docs/specs/usage-statistics.md §4.1, §4.3 — 비로그인도 기록한다. 응답은 항상 같고 본문은 읽지 않는다. */
@WebMvcTest(UsageEventController.class)
@ActiveProfiles("local")
@TestPropertySource(properties = "dabaeum.security.dev.bearer-token=usage-event-token")
@Import({
    ApiExceptionHandler.class,
    ClockConfiguration.class,
    SecurityConfiguration.class,
    DevBearerSecurityConfiguration.class,
    LocalDevSecuritySupportConfiguration.class,
    JacksonConfiguration.class,
    RequestIdFilter.class
})
class UsageEventControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean UsageEventService service;

    @Test
    void anonymousVisitIsRecordedWithoutUser() throws Exception {
        mockMvc.perform(post("/api/v1/visits").header("User-Agent", "junit"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.accepted").value(true));

        verify(service).recordVisit(isNull(), any(), eq("junit"));
    }

    @Test
    void bannerClickFromSendBeaconIgnoresTextBody() throws Exception {
        UUID banner = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/banners/{bannerId}/clicks", banner).header("User-Agent", "junit")
                .contentType(MediaType.TEXT_PLAIN).content(""))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.accepted").value(true));

        verify(service).recordBannerClick(eq(banner), isNull(), any(), eq("junit"));
    }
}
