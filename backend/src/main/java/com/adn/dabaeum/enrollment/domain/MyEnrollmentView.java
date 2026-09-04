package com.adn.dabaeum.enrollment.domain;

import com.adn.dabaeum.course.domain.CourseStatus;
import java.time.Instant;
import java.util.UUID;

public record MyEnrollmentView(
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
