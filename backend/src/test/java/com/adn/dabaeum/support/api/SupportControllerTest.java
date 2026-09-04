package com.adn.dabaeum.support.api;

import static com.atlassian.oai.validator.mockmvc.OpenApiValidationMatchers.openApi;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.adn.dabaeum.common.api.ApiExceptionHandler;
import com.adn.dabaeum.common.config.ClockConfiguration;
import com.adn.dabaeum.common.config.DevBearerSecurityConfiguration;
import com.adn.dabaeum.common.config.JacksonConfiguration;
import com.adn.dabaeum.common.config.SecurityConfiguration;
import com.adn.dabaeum.common.security.AuthenticatedRole;
import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import com.adn.dabaeum.common.security.CurrentUserProvider;
import com.adn.dabaeum.common.web.RequestIdFilter;
import com.adn.dabaeum.support.application.NoticePage;
import com.adn.dabaeum.support.application.SupportApplicationService;
import com.adn.dabaeum.support.domain.Notice;
import com.adn.dabaeum.support.domain.NoticeAudience;
import com.adn.dabaeum.support.domain.NoticeStatus;
import com.adn.dabaeum.testsupport.OpenApiContract;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Set;
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

@WebMvcTest(SupportController.class)
@ActiveProfiles("local")
@TestPropertySource(properties = "dabaeum.security.dev.bearer-token=support-token")
@Import({
    SupportApiMapper.class,
    ApiExceptionHandler.class,
    ClockConfiguration.class,
    SecurityConfiguration.class,
    DevBearerSecurityConfiguration.class,
    JacksonConfiguration.class,
    RequestIdFilter.class
})
class SupportControllerTest {

    private static final Path CONTRACT =
        Path.of("docs/api/dabaeum-api-v1.yaml").toAbsolutePath();
    private static final UUID NOTICE_ID = UUID.fromString(
        "90000000-0000-0000-0000-000000000009");
    private static final String REQUEST_ID = "1f4b5d3f-1c29-48ae-94f6-4a66d9a1e2b1";

    @Autowired MockMvc mockMvc;
    @MockitoBean SupportApplicationService service;
    @MockitoBean CurrentUserProvider currentUserProvider;

    @Test
    void listsPublishedNoticesWithoutAuthentication() throws Exception {
        when(service.listPublishedNotices(any(), anyInt(), anyInt()))
            .thenReturn(new NoticePage(List.of(publishedNotice()), 0, 20, 1, 1));

        mockMvc.perform(get("/api/v1/notices")
                .header(RequestIdFilter.HEADER_NAME, REQUEST_ID))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].title").value("서비스 점검 안내"))
            .andExpect(jsonPath("$.page.totalElements").value(1))
            .andExpect(openApi().isValid(OpenApiContract.VALIDATOR));
    }

    @Test
    void createsNoticeAsPlatformAdmin() throws Exception {
        when(currentUserProvider.requireContext()).thenReturn(admin());
        when(service.createNotice(any())).thenReturn(publishedNotice());

        mockMvc.perform(post("/api/v1/admin/notices")
                .with(user("admin").roles("PLATFORM_ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"서비스 점검 안내\",\"body\":\"본문\","
                    + "\"audience\":\"PUBLIC\",\"status\":\"PUBLISHED\"}")
                .header(RequestIdFilter.HEADER_NAME, REQUEST_ID))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.status").value("PUBLISHED"))
            .andExpect(openApi().isValid(OpenApiContract.VALIDATOR));
    }

    @Test
    void rejectsAdminNoticeAccessWithoutPlatformAdminRole() throws Exception {
        mockMvc.perform(get("/api/v1/admin/notices")
                .with(user("manager").roles("INSTITUTION_ADMIN")))
            .andExpect(status().isForbidden());
    }

    @Test
    void rejectsUnknownTermsTypeWithBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/contents/terms").param("type", "NOPE"))
            .andExpect(status().isBadRequest());
    }

    private Notice publishedNotice() {
        Instant now = Instant.parse("2026-08-27T00:00:00Z");
        return new Notice(
            NOTICE_ID, "서비스 점검 안내", "본문", NoticeAudience.PUBLIC,
            NoticeStatus.PUBLISHED, now, null, now, now);
    }

    private AuthenticatedUserContext admin() {
        return new AuthenticatedUserContext(UUID.fromString(
            "10000000-0000-0000-0000-000000000001"), "LOCAL",
            Set.of(new AuthenticatedRole("PLATFORM_ADMIN", null)));
    }
}
