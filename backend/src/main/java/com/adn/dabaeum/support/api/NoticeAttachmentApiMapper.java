package com.adn.dabaeum.support.api;

import com.adn.dabaeum.common.api.ApiMeta;
import com.adn.dabaeum.common.api.ApiResponse;
import com.adn.dabaeum.support.application.AttachNoticeAttachmentCommand;
import com.adn.dabaeum.support.domain.NoticeAttachment;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** course.api.CourseAttachmentApiMapper 와 같은 모양이다. */
@Component
public class NoticeAttachmentApiMapper {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final Clock clock;

    public NoticeAttachmentApiMapper(Clock clock) {
        this.clock = clock;
    }

    public AttachNoticeAttachmentCommand toAttachCommand(
        UUID noticeId,
        NoticeAttachmentAttachRequest request
    ) {
        return new AttachNoticeAttachmentCommand(noticeId, request.fileId());
    }

    public ApiResponse<NoticeAttachmentResponse> toApiResponse(
        NoticeAttachment attachment,
        String requestId
    ) {
        return new ApiResponse<>(toResponse(attachment), meta(requestId));
    }

    public ApiResponse<List<NoticeAttachmentResponse>> toListResponse(
        List<NoticeAttachment> attachments,
        String requestId
    ) {
        return new ApiResponse<>(
            attachments.stream().map(this::toResponse).toList(),
            meta(requestId)
        );
    }

    private NoticeAttachmentResponse toResponse(NoticeAttachment attachment) {
        return new NoticeAttachmentResponse(
            attachment.id(), attachment.noticeId(), attachment.fileId(),
            attachment.fileName(), attachment.byteSize(), attachment.sortOrder(),
            attachment.createdAt()
        );
    }

    private ApiMeta meta(String requestId) {
        return new ApiMeta(requestId, OffsetDateTime.ofInstant(clock.instant(), SEOUL));
    }
}
