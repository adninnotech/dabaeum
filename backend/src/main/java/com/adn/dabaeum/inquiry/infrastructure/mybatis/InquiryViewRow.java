package com.adn.dabaeum.inquiry.infrastructure.mybatis;

import java.time.Instant;
import java.util.UUID;

public record InquiryViewRow(
    UUID id,
    UUID userId,
    UUID courseId,
    String title,
    String status,
    Instant createdAt,
    String courseTitle,
    String userName
) {
}
