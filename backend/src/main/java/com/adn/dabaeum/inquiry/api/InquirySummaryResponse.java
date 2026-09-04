package com.adn.dabaeum.inquiry.api;

import com.adn.dabaeum.inquiry.domain.InquiryStatus;
import java.time.Instant;
import java.util.UUID;

public record InquirySummaryResponse(
    UUID id,
    UUID courseId,
    String title,
    InquiryStatus status,
    Instant createdAt,
    String courseTitle,
    String userName
) {
}
