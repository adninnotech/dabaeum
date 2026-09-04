package com.adn.dabaeum.institution.api;

import com.adn.dabaeum.institution.domain.InstitutionJoinApplicationStatus;
import java.time.Instant;
import java.util.UUID;

public record InstitutionJoinApplicationResponse(
    UUID id,
    String institutionName,
    String institutionCode,
    String representativeName,
    String contactEmail,
    String contactPhone,
    String address,
    InstitutionJoinApplicationStatus status,
    String rejectionReason,
    UUID createdInstitutionId,
    Instant decidedAt,
    Instant createdAt
) {
}
