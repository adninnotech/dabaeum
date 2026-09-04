package com.adn.dabaeum.common.api;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.adn.dabaeum.common.config.ClockConfiguration;
import com.adn.dabaeum.common.config.SecurityConfiguration;
import com.adn.dabaeum.common.web.RequestIdFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(OpenApiContractController.class)
@ActiveProfiles("dev")
@Import({
    SecurityConfiguration.class,
    ClockConfiguration.class,
    RequestIdFilter.class
})
class DevOpenApiContractControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void servesCanonicalOpenApiContractForDevProfile() throws Exception {
        mockMvc.perform(get("/openapi/dabaeum-api-v1.yaml"))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(
                MediaType.parseMediaType("application/yaml")))
            .andExpect(content().string(containsString("openapi: 3.1.0")))
            .andExpect(content().string(containsString("operationId: listCourses")));
    }
}
