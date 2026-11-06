package com.adn.dabaeum.support.application;

import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import com.adn.dabaeum.support.domain.NoticeAttachment;
import java.util.List;
import java.util.UUID;

public interface NoticeAttachmentApplicationService {

    List<NoticeAttachment> list(UUID noticeId);

    /** 게시된(예약 시각이 지난) 공지의 첨부만. 아니면 공지 상세와 같이 404. */
    List<NoticeAttachment> listPublished(UUID noticeId);

    NoticeAttachment attach(
        AttachNoticeAttachmentCommand command,
        AuthenticatedUserContext context
    );

    NoticeAttachment remove(UUID noticeId, UUID attachmentId, AuthenticatedUserContext context);
}
