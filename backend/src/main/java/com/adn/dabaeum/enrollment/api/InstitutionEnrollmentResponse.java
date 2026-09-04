package com.adn.dabaeum.enrollment.api;

import com.adn.dabaeum.enrollment.domain.EnrollmentStatus;
import java.time.Instant;
import java.util.UUID;

public record InstitutionEnrollmentResponse(
    UUID id,
    UUID courseId,
    UUID userId,
    EnrollmentStatus status,
    Instant appliedAt,
    Instant createdAt,
    String courseTitle,
    String userName
) {
}
