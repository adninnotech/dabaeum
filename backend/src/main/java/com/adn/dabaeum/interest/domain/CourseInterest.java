package com.adn.dabaeum.interest.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record CourseInterest(
    UUID id,
    UUID userId,
    UUID courseId,
    Instant createdAt
) {

    public CourseInterest {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(courseId, "courseId");
        Objects.requireNonNull(createdAt, "createdAt");
    }
}
