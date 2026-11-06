package com.adn.dabaeum.support.api;

import com.adn.dabaeum.common.api.ApiResponse;
import com.adn.dabaeum.common.web.RequestIdFilter;
import com.adn.dabaeum.support.application.NoticeAttachmentApplicationService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 학습자 공지 상세의 첨부파일. 게시된 공지만 보여 주고 인증은 받지 않는다(공지 상세와 같다). */
@RestController
@Profile({"local", "dev"})
@RequestMapping("/api/v1/notices/{noticeId}/attachments")
public class PublicNoticeAttachmentController {

    private final NoticeAttachmentApplicationService service;
    private final NoticeAttachmentApiMapper mapper;

    public PublicNoticeAttachmentController(
        NoticeAttachmentApplicationService service,
        NoticeAttachmentApiMapper mapper
    ) {
        this.service = service;
        this.mapper = mapper;
    }

    @GetMapping
    public ApiResponse<List<NoticeAttachmentResponse>> list(
        @PathVariable UUID noticeId,
        HttpServletRequest servletRequest
    ) {
        return mapper.toListResponse(
            service.listPublished(noticeId),
            (String) servletRequest.getAttribute(RequestIdFilter.ATTRIBUTE_NAME));
    }
}
