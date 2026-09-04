package com.adn.dabaeum.review.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record CourseReview(
    UUID id,
    UUID userId,
    UUID courseId,
    int rating,
    String content,
    Instant createdAt,
    Instant updatedAt
) {

    public CourseReview {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(courseId, "courseId");
        if (rating < 1 || rating > 5) {
            throw new IllegalArgumentException("rating must be between 1 and 5");
        }
        if (content != null && content.length() > 2000) {
            throw new IllegalArgumentException("content must not exceed 2000 characters");
        }
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }
}
