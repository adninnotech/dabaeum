package com.adn.dabaeum.support.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.adn.dabaeum.common.api.ApiErrorCode;
import com.adn.dabaeum.common.api.ApiException;
import com.adn.dabaeum.common.api.ApiExceptionHandler;
import com.adn.dabaeum.common.config.ClockConfiguration;
import com.adn.dabaeum.common.config.DevBearerSecurityConfiguration;
import com.adn.dabaeum.common.config.LocalDevSecuritySupportConfiguration;
import com.adn.dabaeum.common.config.JacksonConfiguration;
import com.adn.dabaeum.common.config.SecurityConfiguration;
import com.adn.dabaeum.common.security.AuthenticatedRole;
import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import com.adn.dabaeum.common.security.CurrentUserProvider;
import com.adn.dabaeum.common.web.RequestIdFilter;
import com.adn.dabaeum.support.application.FaqPage;
import com.adn.dabaeum.support.application.NoticePage;
import com.adn.dabaeum.support.application.SupportApplicationService;
import com.adn.dabaeum.support.domain.Faq;
import com.adn.dabaeum.support.domain.Notice;
import com.adn.dabaeum.support.domain.NoticeAudience;
import com.adn.dabaeum.support.domain.NoticeStatus;
import com.adn.dabaeum.support.domain.TermsContent;
import com.adn.dabaeum.support.domain.TermsType;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
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
    LocalDevSecuritySupportConfiguration.class,
    JacksonConfiguration.class,
    RequestIdFilter.class
})
class SupportControllerTest {

    private static final UUID NOTICE_ID = UUID.fromString(
        "90000000-0000-0000-0000-000000000009");
    private static final UUID FAQ_ID = UUID.fromString(
        "90000000-0000-0000-0000-00000000000f");
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
            .andExpect(jsonPath("$.page.totalElements").value(1));
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
            .andExpect(jsonPath("$.data.status").value("PUBLISHED"));
    }

    @Test
    void returnsADraftNoticeToPlatformAdminForEditing() throws Exception {
        Instant now = Instant.parse("2026-10-21T00:00:00Z");
        when(currentUserProvider.requireContext()).thenReturn(admin());
        when(service.getAdminNotice(eq(NOTICE_ID), any())).thenReturn(new Notice(
            NOTICE_ID, "초안 공지", "본문", NoticeAudience.PUBLIC, NoticeStatus.DRAFT, null, null, null, now, now));

        mockMvc.perform(get("/api/v1/admin/notices/" + NOTICE_ID)
                .with(user("admin").roles("PLATFORM_ADMIN"))
                .header(RequestIdFilter.HEADER_NAME, REQUEST_ID))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("DRAFT"));

        mockMvc.perform(get("/api/v1/admin/notices/" + NOTICE_ID)
                .with(user("manager").roles("INSTITUTION_ADMIN")))
            .andExpect(status().isForbidden());
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

    @Test
    void listsAdminFaqsAsPlatformAdmin() throws Exception {
        when(currentUserProvider.requireContext()).thenReturn(admin());
        when(service.listAdminFaqs(any(), anyInt(), anyInt()))
            .thenReturn(new FaqPage(List.of(faq()), 0, 20, 1, 1));

        mockMvc.perform(get("/api/v1/admin/faqs")
                .with(user("admin").roles("PLATFORM_ADMIN"))
                .header(RequestIdFilter.HEADER_NAME, REQUEST_ID))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].question").value("환불 규정이 어떻게 되나요?"));
    }

    @Test
    void rejectsAdminFaqAccessWithoutPlatformAdminRole() throws Exception {
        mockMvc.perform(get("/api/v1/admin/faqs")
                .with(user("manager").roles("INSTITUTION_ADMIN")))
            .andExpect(status().isForbidden());
    }

    @Test
    void createsFaqAsPlatformAdmin() throws Exception {
        when(currentUserProvider.requireContext()).thenReturn(admin());
        when(service.createFaq(any())).thenReturn(faq());

        mockMvc.perform(post("/api/v1/admin/faqs")
                .with(user("admin").roles("PLATFORM_ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"question\":\"환불 규정이 어떻게 되나요?\",\"answer\":\"본문\","
                    + "\"sortOrder\":1}")
                .header(RequestIdFilter.HEADER_NAME, REQUEST_ID))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.question").value("환불 규정이 어떻게 되나요?"));
    }

    @Test
    void rejectsFaqCreateWithBlankAnswer() throws Exception {
        mockMvc.perform(post("/api/v1/admin/faqs")
                .with(user("admin").roles("PLATFORM_ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"question\":\"질문\",\"answer\":\"\"}"))
            .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void updatesFaqAsPlatformAdmin() throws Exception {
        when(currentUserProvider.requireContext()).thenReturn(admin());
        when(service.updateFaq(any())).thenReturn(faq());

        mockMvc.perform(put("/api/v1/admin/faqs/" + FAQ_ID)
                .with(user("admin").roles("PLATFORM_ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"question\":\"환불 규정이 어떻게 되나요?\",\"answer\":\"본문\","
                    + "\"sortOrder\":1}")
                .header(RequestIdFilter.HEADER_NAME, REQUEST_ID))
            .andExpect(status().isOk());
    }

    @Test
    void returnsNotFoundWhenUpdatingUnknownFaq() throws Exception {
        lenient().when(currentUserProvider.requireContext()).thenReturn(admin());
        lenient().when(service.updateFaq(any())).thenThrow(new ApiException(
            HttpStatus.NOT_FOUND, ApiErrorCode.FAQ_NOT_FOUND, "FAQ not found"));

        mockMvc.perform(put("/api/v1/admin/faqs/" + FAQ_ID)
                .with(user("admin").roles("PLATFORM_ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"question\":\"질문\",\"answer\":\"본문\",\"sortOrder\":1}"))
            .andExpect(status().isNotFound());
    }

    @Test
    void deletesFaqAsPlatformAdmin() throws Exception {
        when(currentUserProvider.requireContext()).thenReturn(admin());

        mockMvc.perform(delete("/api/v1/admin/faqs/" + FAQ_ID)
                .with(user("admin").roles("PLATFORM_ADMIN"))
                .header(RequestIdFilter.HEADER_NAME, REQUEST_ID))
            .andExpect(status().isOk());
    }

    @Test
    void createsTermsContentAsPlatformAdmin() throws Exception {
        when(currentUserProvider.requireContext()).thenReturn(admin());
        when(service.createTerms(any())).thenReturn(terms());

        mockMvc.perform(post("/api/v1/admin/terms")
                .with(user("admin").roles("PLATFORM_ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"USAGE\",\"title\":\"이용약관\",\"body\":\"본문\","
                    + "\"version\":\"1.1\"}")
                .header(RequestIdFilter.HEADER_NAME, REQUEST_ID))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.version").value("1.1"));
    }

    @Test
    void rejectsTermsContentAccessWithoutPlatformAdminRole() throws Exception {
        mockMvc.perform(post("/api/v1/admin/terms")
                .with(user("manager").roles("INSTITUTION_ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"USAGE\",\"title\":\"이용약관\",\"body\":\"본문\","
                    + "\"version\":\"1.1\"}"))
            .andExpect(status().isForbidden());
    }

    @Test
    void rejectsTermsContentCreateWithUnknownType() throws Exception {
        mockMvc.perform(post("/api/v1/admin/terms")
                .with(user("admin").roles("PLATFORM_ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"NOPE\",\"title\":\"이용약관\",\"body\":\"본문\","
                    + "\"version\":\"1.1\"}"))
            .andExpect(status().isUnprocessableEntity());
    }

    private Faq faq() {
        Instant now = Instant.parse("2026-08-27T00:00:00Z");
        return new Faq(FAQ_ID, "환불 규정이 어떻게 되나요?", "본문", 1, now, now);
    }

    private TermsContent terms() {
        Instant now = Instant.parse("2026-08-27T00:00:00Z");
        return new TermsContent(
            UUID.fromString("90000000-0000-0000-0000-000000000010"),
            TermsType.USAGE, "이용약관", "본문", "1.1", now, now);
    }

    private Notice publishedNotice() {
        Instant now = Instant.parse("2026-08-27T00:00:00Z");
        return new Notice(
            NOTICE_ID, "서비스 점검 안내", "본문", NoticeAudience.PUBLIC,
            NoticeStatus.PUBLISHED, now, null, null, now, now);
    }

    private AuthenticatedUserContext admin() {
        return new AuthenticatedUserContext(UUID.fromString(
            "10000000-0000-0000-0000-000000000001"), "LOCAL",
            Set.of(new AuthenticatedRole("PLATFORM_ADMIN", null)));
    }
}
