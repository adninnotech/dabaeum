package com.adn.dabaeum.course.api;

import com.adn.dabaeum.course.domain.CourseInstructorRole;
import java.time.Instant;
import java.util.UUID;

public record CourseInstructorResponse(
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
