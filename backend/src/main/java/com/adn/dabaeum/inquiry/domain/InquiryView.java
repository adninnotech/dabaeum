package com.adn.dabaeum.inquiry.domain;

import java.time.Instant;
import java.util.UUID;

public record InquiryView(
    UUID id,
    UUID userId,
    UUID courseId,
    String title,
    InquiryStatus status,
    Instant createdAt,
    String courseTitle,
    String userName
) {
}
