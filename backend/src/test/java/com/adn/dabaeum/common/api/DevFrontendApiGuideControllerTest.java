package com.adn.dabaeum.common.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.adn.dabaeum.common.config.ClockConfiguration;
import com.adn.dabaeum.common.config.SecurityConfiguration;
import com.adn.dabaeum.common.web.RequestIdFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(FrontendApiGuideController.class)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("dev")
@Import({
    SecurityConfiguration.class,
    ClockConfiguration.class,
    RequestIdFilter.class
})
class DevFrontendApiGuideControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void servesGuideAndMarkdownForDevProfile() throws Exception {
        mockMvc.perform(get("/api-guide/index.html"))
            .andExpect(status().isOk());
        mockMvc.perform(get("/api-guide/dabaeum-frontend-api-flow.md"))
            .andExpect(status().isOk());
    }
}
