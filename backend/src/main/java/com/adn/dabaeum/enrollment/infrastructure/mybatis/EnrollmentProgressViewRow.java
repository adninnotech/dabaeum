package com.adn.dabaeum.enrollment.infrastructure.mybatis;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record EnrollmentProgressViewRow(
    UUID enrollmentId,
    UUID userId,
    String enrolleeName,
    LocalDate enrolleeBirthDate,
    String enrolleeGender,
    String enrolleeEmail,
    String enrolleePhone,
    UUID enrolleeProfileImageId,
    UUID courseId,
    String courseTitle,
    Long attendedCount,
    Long sessionCount,
    Instant lastStudiedAt,
    String status
) {
}
