package com.adn.dabaeum.support.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import com.adn.dabaeum.support.application.NoticeAttachmentApplicationService;
import com.adn.dabaeum.support.application.NoticeCommand;
import com.adn.dabaeum.support.application.NoticePage;
import com.adn.dabaeum.support.application.SupportApplicationService;
import com.adn.dabaeum.support.domain.Notice;
import com.adn.dabaeum.support.domain.NoticeAttachment;
import com.adn.dabaeum.support.domain.NoticeAudience;
import com.adn.dabaeum.support.domain.NoticeStatus;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** 스펙 #32·#33·B-8: 공지 작성자 표시, 첨부파일, 예약 게시, 제목 60자. */
@WebMvcTest({SupportController.class, NoticeAttachmentController.class})
@ActiveProfiles("local")
@TestPropertySource(properties = "dabaeum.security.dev.bearer-token=notice-author-token")
@Import({
    SupportApiMapper.class,
    NoticeAttachmentApiMapper.class,
    ApiExceptionHandler.class,
    ClockConfiguration.class,
    SecurityConfiguration.class,
    DevBearerSecurityConfiguration.class,
    LocalDevSecuritySupportConfiguration.class,
    JacksonConfiguration.class,
    RequestIdFilter.class
})
class NoticeAuthorTest {

    private static final UUID NOTICE_ID = UUID.fromString("70000000-0000-0000-0000-000000000007");
    private static final UUID ADMIN_ID = UUID.fromString("80000000-0000-0000-0000-000000000008");
    private static final UUID FILE_ID = UUID.fromString("90000000-0000-0000-0000-000000000009");
    private static final Instant NOW = Instant.parse("2026-09-18T00:00:00Z");

    @Autowired MockMvc mockMvc;
    @MockitoBean SupportApplicationService service;
    @MockitoBean NoticeAttachmentApplicationService attachmentService;
    @MockitoBean CurrentUserProvider currentUserProvider;

    @Test
    void 공지를_만들면_작성자_이름이_함께_나온다() throws Exception {
        lenient().when(currentUserProvider.requireContext()).thenReturn(admin());
        when(service.createNotice(any())).thenReturn(notice("개발 관리자", null));

        mockMvc.perform(post("/api/v1/admin/notices")
                .with(user("admin").roles("PLATFORM_ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(CREATE_BODY))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.authorName").value("개발 관리자"));
    }

    @Test
    void 작성자를_찾을_수_없으면_이름만_빈다() throws Exception {
        lenient().when(currentUserProvider.requireContext()).thenReturn(admin());
        when(service.listAdminNotices(any(), anyInt(), anyInt(), any(), any(), any()))
            .thenReturn(new NoticePage(List.of(notice(null, null)), 0, 20, 1, 1));

        mockMvc.perform(get("/api/v1/admin/notices")
                .with(user("admin").roles("PLATFORM_ADMIN")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].authorName").doesNotExist());
    }

    @Test
    void 게시일을_미래로_주면_그대로_서비스에_넘어간다() throws Exception {
        lenient().when(currentUserProvider.requireContext()).thenReturn(admin());
        Instant future = Instant.parse("2026-12-25T00:00:00Z");
        when(service.createNotice(any())).thenReturn(notice("개발 관리자", future));

        mockMvc.perform(post("/api/v1/admin/notices")
                .with(user("admin").roles("PLATFORM_ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(SCHEDULED_BODY))
            .andExpect(status().isCreated());

        ArgumentCaptor<NoticeCommand> captor = ArgumentCaptor.forClass(NoticeCommand.class);
        verify(service).createNotice(captor.capture());
        assertThat(captor.getValue().publishedAt()).isEqualTo(future);
    }

    @Test
    void 제목이_60자를_넘으면_422() throws Exception {
        lenient().when(currentUserProvider.requireContext()).thenReturn(admin());
        String longTitle = "가".repeat(61);

        mockMvc.perform(post("/api/v1/admin/notices")
                .with(user("admin").roles("PLATFORM_ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("{\"title\": \"%s\", \"body\": \"본문\", "
                    + "\"audience\": \"PUBLIC\"}", longTitle)))
            .andExpect(status().isUnprocessableEntity());

        mockMvc.perform(put("/api/v1/admin/notices/{noticeId}", NOTICE_ID)
                .with(user("admin").roles("PLATFORM_ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("{\"title\": \"%s\", \"body\": \"본문\", "
                    + "\"audience\": \"PUBLIC\", \"status\": \"DRAFT\"}", longTitle)))
            .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void 첨부파일을_붙이고_목록으로_보고_뗀다() throws Exception {
        lenient().when(currentUserProvider.requireContext()).thenReturn(admin());
        NoticeAttachment attachment = attachment();
        when(attachmentService.list(NOTICE_ID)).thenReturn(List.of(attachment));
        when(attachmentService.attach(any(), any())).thenReturn(attachment);
        when(attachmentService.remove(any(), any(), any())).thenReturn(attachment);

        mockMvc.perform(get("/api/v1/admin/notices/{noticeId}/attachments", NOTICE_ID)
                .with(user("admin").roles("PLATFORM_ADMIN")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].fileName").value("안내문.pdf"));

        mockMvc.perform(post("/api/v1/admin/notices/{noticeId}/attachments", NOTICE_ID)
                .with(user("admin").roles("PLATFORM_ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("{\"fileId\": \"%s\"}", FILE_ID.toString())))
            .andExpect(status().isCreated());

        mockMvc.perform(delete("/api/v1/admin/notices/{noticeId}/attachments/{attachmentId}",
                NOTICE_ID, attachment.id())
                .with(user("admin").roles("PLATFORM_ADMIN")))
            .andExpect(status().isOk());
    }

    @Test
    void 학습자는_공지_첨부를_건드릴_수_없다() throws Exception {
        mockMvc.perform(get("/api/v1/admin/notices/{noticeId}/attachments", NOTICE_ID)
                .with(user("learner").roles("LEARNER")))
            .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/admin/notices/{noticeId}/attachments", NOTICE_ID)
                .with(user("learner").roles("LEARNER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("{\"fileId\": \"%s\"}", FILE_ID.toString())))
            .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/admin/notices/{noticeId}/attachments/{attachmentId}",
                NOTICE_ID, UUID.randomUUID())
                .with(user("learner").roles("LEARNER")))
            .andExpect(status().isForbidden());
    }

    private static final String CREATE_BODY =
        "{\"title\": \"공지 제목\", \"body\": \"본문\", "
        + "\"audience\": \"PUBLIC\", \"status\": \"PUBLISHED\"}";

    private static final String SCHEDULED_BODY =
        "{\"title\": \"예약 공지\", \"body\": \"본문\", \"audience\": \"PUBLIC\", "
        + "\"status\": \"PUBLISHED\", \"publishedAt\": \"2026-12-25T00:00:00Z\"}";

    private static String json(String template, String value) {
        return String.format(template, value);
    }

    private Notice notice(String authorName, Instant publishedAt) {
        return new Notice(
            NOTICE_ID, "공지 제목", "본문", NoticeAudience.PUBLIC, NoticeStatus.PUBLISHED,
            publishedAt == null ? NOW : publishedAt, ADMIN_ID, authorName, NOW, NOW);
    }

    private NoticeAttachment attachment() {
        return new NoticeAttachment(
            UUID.fromString("a0000000-0000-0000-0000-00000000000a"),
            NOTICE_ID, FILE_ID, "안내문.pdf", 1024L, 0, NOW);
    }

    private AuthenticatedUserContext admin() {
        return new AuthenticatedUserContext(ADMIN_ID, "LOCAL",
            Set.of(new AuthenticatedRole("PLATFORM_ADMIN", null)));
    }
}
