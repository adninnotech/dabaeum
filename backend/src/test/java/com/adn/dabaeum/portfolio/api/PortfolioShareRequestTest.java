package com.adn.dabaeum.portfolio.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.adn.dabaeum.common.api.ApiExceptionHandler;
import com.adn.dabaeum.common.config.ClockConfiguration;
import com.adn.dabaeum.common.config.DevBearerSecurityConfiguration;
import com.adn.dabaeum.common.config.JacksonConfiguration;
import com.adn.dabaeum.common.config.LocalDevSecuritySupportConfiguration;
import com.adn.dabaeum.common.config.SecurityConfiguration;
import com.adn.dabaeum.common.security.AuthenticatedUserPrincipal;
import com.adn.dabaeum.common.security.CurrentUserProvider;
import com.adn.dabaeum.common.web.RequestIdFilter;
import com.adn.dabaeum.portfolio.application.PortfolioService;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** 공유 기간은 정수만 받는다. 1.5 를 1 로 버려 링크를 만들지 않는다 (QA VAL-06). */
@WebMvcTest(PortfolioController.class)
@ActiveProfiles("local")
@TestPropertySource(properties = "dabaeum.security.dev.bearer-token=portfolio-share-request-test")
@Import({
    ApiExceptionHandler.class,
    ClockConfiguration.class,
    SecurityConfiguration.class,
    DevBearerSecurityConfiguration.class,
    LocalDevSecuritySupportConfiguration.class,
    JacksonConfiguration.class,
    RequestIdFilter.class
})
class PortfolioShareRequestTest {

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID SHARE_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Autowired MockMvc mockMvc;
    @MockitoBean PortfolioService service;
    @MockitoBean CurrentUserProvider currentUserProvider;

    @Test
    void rejectsFractionalExpiresInDaysBeforeTheService() throws Exception {
        mockMvc.perform(post("/api/v1/users/me/portfolio/shares")
                .with(learner())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"expiresInDays\": 1.5}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
        verifyNoInteractions(service);
    }

    @Test
    void acceptsWholeExpiresInDays() throws Exception {
        when(currentUserProvider.requireUserId()).thenReturn(USER_ID);
        when(service.createShare(eq(USER_ID), any(), any(), eq(30)))
            .thenReturn(new PortfolioService.CreatedShare(SHARE_ID, "t".repeat(43), Instant.parse("2026-11-20T00:00:00Z")));

        mockMvc.perform(post("/api/v1/users/me/portfolio/shares")
                .with(learner())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"expiresInDays\": 30}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.shareId").value(SHARE_ID.toString()));
        verify(service).createShare(eq(USER_ID), any(), any(), eq(30));
    }

    private static RequestPostProcessor learner() {
        return authentication(new UsernamePasswordAuthenticationToken(
            new AuthenticatedUserPrincipal(USER_ID, Set.of("ROLE_LEARNER")),
            "N/A",
            Set.of(new SimpleGrantedAuthority("ROLE_LEARNER"))));
    }
}
