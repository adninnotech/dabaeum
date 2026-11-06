package com.adn.dabaeum.support.application;

import java.util.UUID;

public record AttachNoticeAttachmentCommand(
    UUID noticeId,
    UUID fileId
) {
}
