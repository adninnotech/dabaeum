package com.adn.dabaeum.enrollment.api;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record LearningCourseResponse(
    UUID enrollmentId,
    UUID courseId,
    String title,
    String institutionName,
    String status,
    String educationType,
    LocalDate startDate,
    LocalDate endDate,
    Instant appliedAt
) {
}
