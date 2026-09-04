package com.adn.dabaeum.enrollment.infrastructure.mybatis;

import java.time.Instant;
import java.util.UUID;

public record InstitutionEnrollmentViewRow(
    UUID id,
    UUID courseId,
    UUID userId,
    String status,
    Instant appliedAt,
    Instant createdAt,
    String courseTitle,
    String userName
) {
}
