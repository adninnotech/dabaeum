package com.adn.dabaeum.enrollment.infrastructure.mybatis;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record EnrollmentListItemRow(
    UUID id,
    UUID courseId,
    UUID userId,
    UUID appliedBy,
    String applicationType,
    String status,
    Instant appliedAt,
    Instant approvedAt,
    Instant rejectedAt,
    Instant cancelledAt,
    Instant withdrawnAt,
    String rejectionReason,
    String cancellationReason,
    Instant createdAt,
    Instant updatedAt,
    Integer waitlistPosition,
    String enrolleeName,
    LocalDate enrolleeBirthDate,
    String enrolleeGender,
    String enrolleeEmail,
    String enrolleePhone,
    UUID enrolleeProfileImageId,
    Long attendedCount,
    Long sessionCount,
    Instant lastStudiedAt
) {
}
