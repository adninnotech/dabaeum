package com.adn.dabaeum.interest.api;

import java.time.Instant;
import java.util.UUID;

public record InterestResponse(
    UUID id,
    UUID courseId,
    Instant createdAt,
    String courseTitle,
    String institutionName,
    String category,
    String educationType,
    String courseStatus
) {
}
