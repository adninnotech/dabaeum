package com.adn.dabaeum.inquiry.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record Inquiry(
    UUID id,
    UUID userId,
    UUID courseId,
    String title,
    String content,
    InquiryStatus status,
    String answer,
    UUID answeredBy,
    Instant answeredAt,
    Instant createdAt,
    Instant updatedAt
) {

    public Inquiry {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(userId, "userId");
        if (title == null || title.isBlank() || title.length() > 200) {
            throw new IllegalArgumentException("title must be 1..200 characters");
        }
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("content must not be blank");
        }
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (status == InquiryStatus.ANSWERED
            && (answer == null || answeredBy == null || answeredAt == null)) {
            throw new IllegalArgumentException(
                "answered inquiry requires answer, answeredBy and answeredAt");
        }
        if (status == InquiryStatus.PENDING
            && (answer != null || answeredBy != null || answeredAt != null)) {
            throw new IllegalArgumentException(
                "pending inquiry must not have answer data");
        }
    }
}
