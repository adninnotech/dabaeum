package com.adn.dabaeum.enrollment.api;

import com.adn.dabaeum.enrollment.domain.EnrollmentApplicationType;
import com.adn.dabaeum.enrollment.domain.EnrollmentStatus;
import java.time.Instant;
import java.util.UUID;

public record EnrollmentResponse(
    UUID id,
    UUID courseId,
    UUID userId,
    UUID appliedBy,
    EnrollmentApplicationType applicationType,
    EnrollmentStatus status,
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
