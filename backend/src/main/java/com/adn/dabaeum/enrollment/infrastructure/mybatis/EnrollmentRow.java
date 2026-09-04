package com.adn.dabaeum.enrollment.infrastructure.mybatis;

import java.time.Instant;
import java.util.UUID;

public record EnrollmentRow(
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
    Instant updatedAt
) {
}
