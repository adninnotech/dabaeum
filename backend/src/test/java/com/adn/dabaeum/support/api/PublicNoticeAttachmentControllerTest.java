package com.adn.dabaeum.support.api;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.adn.dabaeum.common.api.ApiErrorCode;
import com.adn.dabaeum.common.api.ApiException;
import com.adn.dabaeum.common.api.ApiExceptionHandler;
import com.adn.dabaeum.common.config.ClockConfiguration;
import com.adn.dabaeum.common.config.DevBearerSecurityConfiguration;
import com.adn.dabaeum.common.config.JacksonConfiguration;
import com.adn.dabaeum.common.config.LocalDevSecuritySupportConfiguration;
import com.adn.dabaeum.common.config.SecurityConfiguration;
import com.adn.dabaeum.common.security.CurrentUserProvider;
import com.adn.dabaeum.common.web.RequestIdFilter;
import com.adn.dabaeum.support.application.NoticeAttachmentApplicationService;
import com.adn.dabaeum.support.domain.NoticeAttachment;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** 학습자 공지 상세의 첨부 목록. 인증 없이 열리고 게시 전 공지는 404 다. */
@WebMvcTest(PublicNoticeAttachmentController.class)
@ActiveProfiles("local")
@TestPropertySource(properties = "dabaeum.security.dev.bearer-token=support-token")
@Import({
    NoticeAttachmentApiMapper.class,
    ApiExceptionHandler.class,
    ClockConfiguration.class,
    SecurityConfiguration.class,
    DevBearerSecurityConfiguration.class,
    LocalDevSecuritySupportConfiguration.class,
    JacksonConfiguration.class,
    RequestIdFilter.class
})
class PublicNoticeAttachmentControllerTest {

    private static final UUID NOTICE_ID = UUID.fromString("90000000-0000-0000-0000-000000000009");
    private static final String REQUEST_ID = "1f4b5d3f-1c29-48ae-94f6-4a66d9a1e2b1";

    @Autowired MockMvc mockMvc;
    @MockitoBean NoticeAttachmentApplicationService service;
    @MockitoBean CurrentUserProvider currentUserProvider;

    @Test
    void 게시된_공지의_첨부_목록을_인증_없이_준다() throws Exception {
        when(service.listPublished(NOTICE_ID)).thenReturn(List.of(new NoticeAttachment(
            UUID.randomUUID(), NOTICE_ID, UUID.randomUUID(), "안내문.pdf", 1024L, 0,
            Instant.parse("2026-10-01T00:00:00Z"))));

        mockMvc.perform(get("/api/v1/notices/{noticeId}/attachments", NOTICE_ID)
                .header(RequestIdFilter.HEADER_NAME, REQUEST_ID))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].fileName").value("안내문.pdf"));
    }

    @Test
    void 게시_전_공지는_404_다() throws Exception {
        when(service.listPublished(NOTICE_ID)).thenThrow(new ApiException(
            HttpStatus.NOT_FOUND, ApiErrorCode.NOTICE_NOT_FOUND, "Notice not found"));

        mockMvc.perform(get("/api/v1/notices/{noticeId}/attachments", NOTICE_ID)
                .header(RequestIdFilter.HEADER_NAME, REQUEST_ID))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOTICE_NOT_FOUND"));
    }
}
