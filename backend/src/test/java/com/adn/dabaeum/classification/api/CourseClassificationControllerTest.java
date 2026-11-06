package com.adn.dabaeum.classification.api;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.adn.dabaeum.classification.application.CourseClassificationNode;
import com.adn.dabaeum.classification.application.CourseClassificationService;
import com.adn.dabaeum.common.api.ApiExceptionHandler;
import com.adn.dabaeum.common.config.ClockConfiguration;
import com.adn.dabaeum.common.config.DevBearerSecurityConfiguration;
import com.adn.dabaeum.common.config.JacksonConfiguration;
import com.adn.dabaeum.common.config.LocalDevSecuritySupportConfiguration;
import com.adn.dabaeum.common.config.SecurityConfiguration;
import com.adn.dabaeum.common.web.RequestIdFilter;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** 설계서 §12.4 — 분류 트리(공개), 전체 트리·사용 여부(플랫폼 관리자). */
@WebMvcTest(CourseClassificationController.class)
@ActiveProfiles("local")
@TestPropertySource(properties = "dabaeum.security.dev.bearer-token=classification-token")
@Import({
    ApiExceptionHandler.class,
    ClockConfiguration.class,
    SecurityConfiguration.class,
    DevBearerSecurityConfiguration.class,
    LocalDevSecuritySupportConfiguration.class,
    JacksonConfiguration.class,
    RequestIdFilter.class
})
class CourseClassificationControllerTest {

    private static final CourseClassificationNode CULTURE = new CourseClassificationNode(
        "TSIX_040000", 1, "문화예술교육", true, List.of(
            new CourseClassificationNode("TSIX_040200", 2, "생활문화예술", true, List.of(
                new CourseClassificationNode("TSIX_040201", 3, "생활공예", true, List.of())))));

    @Autowired MockMvc mockMvc;
    @MockitoBean CourseClassificationService service;

    @Test
    void publicTreeNeedsNoLogin() throws Exception {
        when(service.tree(true)).thenReturn(List.of(CULTURE));

        mockMvc.perform(get("/api/v1/course-classifications"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].children[0].children[0].code").value("TSIX_040201"));
    }

    @Test
    void adminSeesDisabledNodesToo() throws Exception {
        when(service.tree(false)).thenReturn(List.of(CULTURE));

        mockMvc.perform(get("/api/v1/admin/course-classifications").with(user("admin").roles("PLATFORM_ADMIN")))
            .andExpect(status().isOk());
    }

    @Test
    void adminDisablesANode() throws Exception {
        when(service.setEnabled("TSIX_040200", false)).thenReturn(
            new CourseClassificationNode("TSIX_040200", 2, "생활문화예술", false, List.of()));

        mockMvc.perform(post("/api/v1/admin/course-classifications/TSIX_040200/disable")
                .with(user("admin").roles("PLATFORM_ADMIN")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.enabled").value(false));
    }

    @Test
    void learnerCannotChangeUsage() throws Exception {
        mockMvc.perform(post("/api/v1/admin/course-classifications/TSIX_040200/enable")
                .with(user("learner").roles("LEARNER")))
            .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
}
