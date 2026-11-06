package com.adn.dabaeum.administrator.infrastructure.mybatis;

import java.time.Instant;
import java.util.UUID;

public record AdministratorRow(
    UUID userId,
    String name,
    String email,
    String status,
    Instant lastLoginAt,
    Instant lockedUntil,
    Instant passwordChangedAt,
    Instant createdAt
) {
}
