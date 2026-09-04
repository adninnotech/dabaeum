package com.adn.dabaeum.system.api;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.adn.dabaeum.common.config.ClockConfiguration;
import com.adn.dabaeum.common.config.SecurityConfiguration;
import com.adn.dabaeum.common.web.RequestIdFilter;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@WebMvcTest(SystemPingController.class)
@Import({
    SecurityConfiguration.class,
    ClockConfiguration.class,
    RequestIdFilter.class
})
class SystemPingControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void generatesRequestIdAndReturnsStandardResponseWhenHeaderIsAbsent() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/system/ping"))
            .andExpect(status().isOk())
            .andExpect(header().string("X-Request-Id", matchesPattern("^[0-9a-f-]{36}$")))
            .andExpect(jsonPath("$.data.status").value("OK"))
            .andExpect(jsonPath("$.meta.requestId").isNotEmpty())
            .andExpect(jsonPath("$.meta.timestamp").isNotEmpty())
            .andReturn();

        UUID.fromString(result.getResponse().getHeader("X-Request-Id"));
    }

    @Test
    void reusesValidRequestIdInResponseHeaderAndBody() throws Exception {
        String requestId = "8b706c0e-96cf-4d0a-b357-d80752facf1c";

        mockMvc.perform(get("/api/v1/system/ping").header("X-Request-Id", requestId))
            .andExpect(status().isOk())
            .andExpect(header().string("X-Request-Id", requestId))
            .andExpect(jsonPath("$.meta.requestId").value(requestId));
    }

    @Test
    void replacesInvalidRequestIdInResponseHeaderAndBody() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/system/ping")
                .header("X-Request-Id", "not-a-uuid"))
            .andExpect(status().isOk())
            .andExpect(header().string("X-Request-Id", matchesPattern("^[0-9a-f-]{36}$")))
            .andReturn();

        String responseRequestId = result.getResponse().getHeader("X-Request-Id");
        UUID.fromString(responseRequestId);
        org.assertj.core.api.Assertions.assertThat(responseRequestId).isNotEqualTo("not-a-uuid");
        org.assertj.core.api.Assertions.assertThat(result.getResponse().getContentAsString())
            .contains("\"requestId\":\"" + responseRequestId + "\"");
    }

    @Test
    void returnsTimestampWithSeoulOffset() throws Exception {
        mockMvc.perform(get("/api/v1/system/ping"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.meta.timestamp", matchesPattern(".*\\+09:00$")));
    }
}
