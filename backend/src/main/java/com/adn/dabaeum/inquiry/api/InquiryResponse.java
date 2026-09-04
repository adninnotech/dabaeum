package com.adn.dabaeum.inquiry.api;

import com.adn.dabaeum.inquiry.domain.InquiryStatus;
import java.time.Instant;
import java.util.UUID;

public record InquiryResponse(
    UUID id,
    UUID userId,
    UUID courseId,
    String title,
    String content,
    InquiryStatus status,
    String answer,
    Instant answeredAt,
    Instant createdAt
) {
}
