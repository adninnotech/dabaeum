package com.adn.dabaeum.support.api;

import java.time.Instant;
import java.util.UUID;

public record NoticeAttachmentResponse(
    UUID id,
    UUID noticeId,
    UUID fileId,
    String fileName,
    long byteSize,
    int sortOrder,
    Instant createdAt
) {
}
