package com.adn.dabaeum.common.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.adn.dabaeum.common.config.ClockConfiguration;
import com.adn.dabaeum.common.config.SecurityConfiguration;
import com.adn.dabaeum.common.web.RequestIdFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(FrontendApiGuideController.class)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("local")
@Import({
    SecurityConfiguration.class,
    ClockConfiguration.class,
    RequestIdFilter.class
})
class FrontendApiGuideControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void guideControllerIsLimitedToLocalAndDevProfiles() {
        Profile profile = FrontendApiGuideController.class
            .getAnnotation(Profile.class);

        assertThat(profile).isNotNull();
        assertThat(profile.value()).containsExactlyInAnyOrder("local", "dev");
    }

    @Test
    void servesGuideAssetsAndMarkdownDownloadForLocalProfile() throws Exception {
        mockMvc.perform(get("/api-guide/index.html"))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
            .andExpect(content().string(containsString(
                "다배움 프론트엔드 API 호출 흐름")));

        mockMvc.perform(get("/api-guide/api-guide.css"))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(
                MediaType.parseMediaType("text/css")));

        mockMvc.perform(get("/api-guide/api-guide.js"))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(
                MediaType.parseMediaType("text/javascript")));

        mockMvc.perform(get("/api-guide/dabaeum-frontend-api-flow.md"))
            .andExpect(status().isOk())
            .andExpect(header().string(
                HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"dabaeum-frontend-api-flow.md\""))
            .andExpect(content().contentTypeCompatibleWith(
                MediaType.parseMediaType("text/markdown;charset=UTF-8")))
            .andExpect(content().string(containsString(
                "# 다배움 프론트엔드 API 호출 흐름")));
    }
}
