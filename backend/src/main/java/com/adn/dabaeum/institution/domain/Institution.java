package com.adn.dabaeum.institution.domain;

import java.time.Instant;
import java.util.UUID;

public record Institution(
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
    Instant updatedAt,
    Instant deletedAt
) {
}
