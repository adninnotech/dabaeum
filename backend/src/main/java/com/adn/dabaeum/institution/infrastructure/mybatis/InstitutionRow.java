package com.adn.dabaeum.institution.infrastructure.mybatis;

import java.time.Instant;
import java.util.UUID;

public record InstitutionRow(
    UUID id,
    String institutionCode,
    String name,
    String businessNumber,
    String representativeName,
    String address,
    String contactPhone,
    String contactEmail,
    String status,
    Instant createdAt,
    Instant updatedAt,
    Instant deletedAt
) {
}
