package com.adn.dabaeum.enrollment.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record LearningCourseView(
    UUID enrollmentId,
    UUID courseId,
    String title,
    String institutionName,
    String learningStatus,
    String educationType,
    LocalDate startDate,
    LocalDate endDate,
    Instant appliedAt
) {
}
