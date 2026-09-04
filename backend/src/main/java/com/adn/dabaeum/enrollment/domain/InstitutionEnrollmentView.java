package com.adn.dabaeum.enrollment.domain;

import java.time.Instant;
import java.util.UUID;

public record InstitutionEnrollmentView(
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
