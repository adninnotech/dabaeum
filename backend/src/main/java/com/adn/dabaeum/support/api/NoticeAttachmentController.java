package com.adn.dabaeum.support.api;

import com.adn.dabaeum.common.api.ApiResponse;
import com.adn.dabaeum.common.security.CurrentUserProvider;
import com.adn.dabaeum.common.web.RequestIdFilter;
import com.adn.dabaeum.support.application.NoticeAttachmentApplicationService;
import com.adn.dabaeum.support.domain.NoticeAttachment;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** B-8 공지 작성 화면의 첨부파일. course.api.CourseAttachmentController 와 같은 모양이다. */
@RestController
@Profile({"local", "dev"})
@RequestMapping("/api/v1/admin/notices/{noticeId}/attachments")
public class NoticeAttachmentController {

    private final NoticeAttachmentApplicationService service;
    private final NoticeAttachmentApiMapper mapper;
    private final CurrentUserProvider currentUserProvider;

    public NoticeAttachmentController(
        NoticeAttachmentApplicationService service,
        NoticeAttachmentApiMapper mapper,
        CurrentUserProvider currentUserProvider
    ) {
        this.service = service;
        this.mapper = mapper;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping
    public ApiResponse<List<NoticeAttachmentResponse>> list(
        @PathVariable UUID noticeId,
        HttpServletRequest servletRequest
    ) {
        return mapper.toListResponse(service.list(noticeId), requestId(servletRequest));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<NoticeAttachmentResponse>> attach(
        @PathVariable UUID noticeId,
        @Valid @RequestBody NoticeAttachmentAttachRequest request,
        HttpServletRequest servletRequest
    ) {
        NoticeAttachment attached = service.attach(
            mapper.toAttachCommand(noticeId, request),
            currentUserProvider.requireContext()
        );
        return ResponseEntity
            .created(URI.create(
                "/api/v1/admin/notices/" + noticeId + "/attachments/" + attached.id()))
            .body(mapper.toApiResponse(attached, requestId(servletRequest)));
    }

    @DeleteMapping("/{attachmentId}")
    public ApiResponse<NoticeAttachmentResponse> remove(
        @PathVariable UUID noticeId,
        @PathVariable UUID attachmentId,
        HttpServletRequest servletRequest
    ) {
        NoticeAttachment removed = service.remove(
            noticeId, attachmentId, currentUserProvider.requireContext());
        return mapper.toApiResponse(removed, requestId(servletRequest));
    }

    private String requestId(HttpServletRequest request) {
        return (String) request.getAttribute(RequestIdFilter.ATTRIBUTE_NAME);
    }
}
