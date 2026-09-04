package com.adn.dabaeum.interest.infrastructure.mybatis;

import java.time.Instant;
import java.util.UUID;

public record CourseInterestRow(
    UUID id,
    UUID userId,
    UUID courseId,
    Instant createdAt
) {
}
