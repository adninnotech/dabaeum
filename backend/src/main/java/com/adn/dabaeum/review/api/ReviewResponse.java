package com.adn.dabaeum.review.api;

import java.time.Instant;
import java.util.UUID;

public record ReviewResponse(
    UUID id,
    UUID courseId,
    int rating,
    String content,
    Instant createdAt,
    String courseTitle,
    String userName
) {
}
