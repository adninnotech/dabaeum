package com.adn.dabaeum.enrollment.api;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record LearningCourseResponse(
    UUID enrollmentId,
    UUID courseId,
    String title,
    String status,
    LocalDate startDate,
    LocalDate endDate,
    Instant appliedAt,
    long attendedCount,
    int progressPercent,
    @JsonUnwrapped CourseCardFields card
) {
}
