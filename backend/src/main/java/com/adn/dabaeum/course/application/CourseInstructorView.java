package com.adn.dabaeum.course.application;

import com.adn.dabaeum.course.domain.CourseInstructorRole;
import java.time.Instant;
import java.util.UUID;

public record CourseInstructorView(
    UUID id,
    UUID courseId,
    UUID userId,
    String instructorName,
    String instructorEmail,
    CourseInstructorRole role,
    Instant assignedAt,
    Instant createdAt
) {
}
