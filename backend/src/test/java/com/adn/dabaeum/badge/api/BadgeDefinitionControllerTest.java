package com.adn.dabaeum.badge.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.adn.dabaeum.badge.application.BadgeDefinitionService;
import com.adn.dabaeum.badge.domain.BadgeDefinition;
import com.adn.dabaeum.badge.domain.BadgeRule;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** 설계서 §11.3 — 도감(공개)과 정의 관리(플랫폼 관리자). */
@WebMvcTest(BadgeDefinitionController.class)
@ActiveProfiles("local")
@TestPropertySource(properties = "dabaeum.security.dev.bearer-token=badge-definition-token")
@Import({
    ApiExceptionHandler.class,
    ClockConfiguration.class,
    SecurityConfiguration.class,
    DevBearerSecurityConfiguration.class,
    LocalDevSecuritySupportConfiguration.class,
    JacksonConfiguration.class,
    RequestIdFilter.class
})
class BadgeDefinitionControllerTest {

    private static final Instant NOW = Instant.parse("2026-09-28T00:00:00Z");
    private static final UUID IMAGE = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Autowired MockMvc mockMvc;
    @MockitoBean BadgeDefinitionService service;

    @Test
    void catalogIsPublic() throws Exception {
        when(service.list(true)).thenReturn(List.of(definition()));

        mockMvc.perform(get("/api/v1/badge-definitions"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].rule.type").value("CUMULATIVE"))
            .andExpect(jsonPath("$.data[0].imageUrl").value("/api/v1/files/" + IMAGE + "/content"));
    }

    @Test
    void adminCreatesDefinition() throws Exception {
        when(service.create(any())).thenReturn(definition());

        mockMvc.perform(post("/api/v1/admin/badge-definitions")
                .with(user("admin").roles("PLATFORM_ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"code":"CULTURE_3","name":"문화예술 3과목","imageFileId":"%s",
                     "rule":{"type":"CUMULATIVE","count":3,"classificationCode":"TSIX_040000"}}
                    """.formatted(IMAGE)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.code").value("CULTURE_3"));
    }

    @Test
    void learnerCannotManageDefinitions() throws Exception {
        mockMvc.perform(get("/api/v1/admin/badge-definitions").with(user("learner").roles("LEARNER")))
            .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    private static BadgeDefinition definition() {
        return new BadgeDefinition(UUID.randomUUID(), "CULTURE_3", "문화예술 3과목", "문화예술 강좌 3개 수료", IMAGE,
            new BadgeRule.Cumulative(3, "TSIX_040000"), true, NOW, NOW);
    }
}
