package com.adn.dabaeum.authentication.infrastructure.mybatis;

import java.time.Instant;
import java.util.UUID;

public record PasswordResetTokenRow(
    UUID id,
    UUID userId,
    String tokenHash,
    Instant expiresAt,
    Instant usedAt,
    Instant createdAt
) {
}
