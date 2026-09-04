package com.adn.dabaeum.review.domain;

import java.time.Instant;
import java.util.UUID;

public record CourseReviewView(
    UUID id,
    UUID userId,
    UUID courseId,
    int rating,
    String content,
    Instant createdAt,
    String courseTitle,
    String userName
) {
}
