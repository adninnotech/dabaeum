package com.adn.dabaeum.instructor.infrastructure.mybatis;

import java.time.Instant;
import java.util.UUID;

public record InstructorApplicationRow(
    UUID id,
    UUID userId,
    UUID institutionId,
    String status,
    String applicationMessage,
    String rejectionReason,
    UUID reviewedBy,
    Instant appliedAt,
    Instant reviewedAt,
    Instant createdAt,
    Instant updatedAt
) {
}
