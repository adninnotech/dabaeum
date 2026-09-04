package com.adn.dabaeum.interest.domain;

import java.time.Instant;
import java.util.UUID;

public record CourseInterestView(
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
