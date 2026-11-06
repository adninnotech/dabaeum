package com.adn.dabaeum.authentication.infrastructure.mybatis;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record DadaeguRegistrationTokenRow(
    UUID id,
    String tokenHash,
    String did,
    String name,
    String phone,
    LocalDate birthDate,
    Instant expiresAt,
    Instant usedAt,
    Instant createdAt
) {
}
