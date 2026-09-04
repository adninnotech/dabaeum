package com.adn.dabaeum.authentication.infrastructure.mybatis;

import java.time.Instant;
import java.util.UUID;

public record LocalAccountRow(
    UUID identityId,
    UUID userId,
    String normalizedEmail,
    String passwordHash,
    Instant createdAt,
    Instant updatedAt
) {
}
