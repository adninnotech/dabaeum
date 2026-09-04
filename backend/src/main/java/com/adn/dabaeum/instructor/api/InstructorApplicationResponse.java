package com.adn.dabaeum.instructor.api;

import com.adn.dabaeum.instructor.domain.InstructorApplicationStatus;
import java.time.Instant;
import java.util.UUID;

public record InstructorApplicationResponse(
    UUID id,
    UUID userId,
    String applicantName,
    String applicantEmail,
    String applicantPhone,
    UUID institutionId,
    InstructorApplicationStatus status,
    String applicationMessage,
    String rejectionReason,
    UUID reviewedBy,
    Instant appliedAt,
    Instant reviewedAt,
    Instant createdAt,
    Instant updatedAt
) {
}
