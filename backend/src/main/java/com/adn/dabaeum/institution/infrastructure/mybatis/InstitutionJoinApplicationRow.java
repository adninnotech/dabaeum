package com.adn.dabaeum.institution.infrastructure.mybatis;

import java.time.Instant;
import java.util.UUID;

public record InstitutionJoinApplicationRow(
    UUID id,
    String institutionName,
    String institutionCode,
    String representativeName,
    String contactEmail,
    String contactPhone,
    String address,
    String status,
    String rejectionReason,
    UUID applicantUserId,
    UUID decidedBy,
    Instant decidedAt,
    UUID createdInstitutionId,
    Instant createdAt,
    Instant updatedAt
) {
}
