package com.adn.dabaeum.course.infrastructure.mybatis;

import com.adn.dabaeum.course.domain.CourseInstructor;
import com.adn.dabaeum.course.domain.CourseInstructorRole;
import java.time.Instant;
import java.util.UUID;

public record CourseInstructorRow(
    UUID id,
    UUID courseId,
    UUID userId,
    String instructorRole,
    Instant assignedAt,
    Instant createdAt
) {

    CourseInstructor toDomain() {
        return new CourseInstructor(
            id,
            courseId,
            userId,
            CourseInstructorRole.valueOf(instructorRole),
            assignedAt,
            createdAt
        );
    }
}
