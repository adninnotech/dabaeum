package com.adn.dabaeum.interest.infrastructure.mybatis;

import java.time.Instant;
import java.util.UUID;

public record CourseInterestViewRow(
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
