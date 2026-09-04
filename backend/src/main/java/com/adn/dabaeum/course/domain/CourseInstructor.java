package com.adn.dabaeum.course.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record CourseInstructor(
    UUID id,
    UUID courseId,
    UUID userId,
    CourseInstructorRole role,
    Instant assignedAt,
    Instant createdAt
) {

    public CourseInstructor {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(courseId, "courseId");
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(assignedAt, "assignedAt");
        Objects.requireNonNull(createdAt, "createdAt");
    }
}
