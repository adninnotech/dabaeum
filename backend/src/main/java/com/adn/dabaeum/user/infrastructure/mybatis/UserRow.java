package com.adn.dabaeum.user.infrastructure.mybatis;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record UserRow(
    UUID id,
    String name,
    String email,
    String phone,
    LocalDate birthDate,
    String status,
    Instant withdrawnAt,
    Instant createdAt,
    Instant updatedAt,
    String career,
    String introduction,
    UUID profileImageId
) {
}
