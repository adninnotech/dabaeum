package com.adn.dabaeum.enrollment.api;

import com.adn.dabaeum.course.domain.CourseStatus;
import com.adn.dabaeum.enrollment.domain.EnrollmentStatus;
import java.time.Instant;
import java.util.UUID;

public record MyEnrollmentResponse(
    UUID id,
    UUID courseId,
    UUID userId,
    EnrollmentStatus status,
    Instant appliedAt,
    Instant createdAt,
    String courseTitle,
    String courseCode,
    CourseStatus courseStatus,
    String institutionName
) {
}
