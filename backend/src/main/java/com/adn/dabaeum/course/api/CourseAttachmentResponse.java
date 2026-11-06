package com.adn.dabaeum.course.api;

import java.time.Instant;
import java.util.UUID;

public record CourseAttachmentResponse(
    UUID id,
    UUID courseId,
    UUID fileId,
    String fileName,
    long byteSize,
    int sortOrder,
    Instant createdAt
) {
}
