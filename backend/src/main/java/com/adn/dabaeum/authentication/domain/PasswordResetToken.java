package com.adn.dabaeum.authentication.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record PasswordResetToken(
    UUID id,
    UUID userId,
    String tokenHash,
    Instant expiresAt,
    Instant usedAt,
    Instant createdAt
) {

    public PasswordResetToken {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(userId, "userId");
        if (tokenHash == null || tokenHash.length() != 64) {
            throw new IllegalArgumentException("tokenHash must be a 64-character hash");
        }
        Objects.requireNonNull(expiresAt, "expiresAt");
        Objects.requireNonNull(createdAt, "createdAt");
    }

    public boolean isUsable(Instant now) {
        return usedAt == null && expiresAt.isAfter(now);
    }
}
