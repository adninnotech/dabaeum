package com.adn.dabaeum.inquiry.infrastructure.mybatis;

import java.time.Instant;
import java.util.UUID;

public record InquiryRow(
    UUID id,
    UUID userId,
    UUID courseId,
    String title,
    String content,
    String status,
    String answer,
    UUID answeredBy,
    Instant answeredAt,
    Instant createdAt,
    Instant updatedAt
) {
}
