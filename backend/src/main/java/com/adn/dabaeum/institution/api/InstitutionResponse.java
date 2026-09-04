package com.adn.dabaeum.institution.api;

import com.adn.dabaeum.institution.domain.InstitutionStatus;
import java.time.Instant;
import java.util.UUID;

public record InstitutionResponse(
    UUID id,
    String institutionCode,
    String name,
    String businessNumber,
    String representativeName,
    String address,
    String contactPhone,
    String contactEmail,
    InstitutionStatus status,
    Instant createdAt,
    Instant updatedAt
) {
}
