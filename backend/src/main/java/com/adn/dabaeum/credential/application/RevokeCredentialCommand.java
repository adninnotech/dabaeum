package com.adn.dabaeum.credential.application;

import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record RevokeCredentialCommand(
    UUID credentialId,
    String reason,
    String idempotencyKey,
    AuthenticatedUserContext actor,
    Instant requestedAt
) {
    public RevokeCredentialCommand {
        Objects.requireNonNull(credentialId, "credentialId");
        reason = requireReason(reason);
        idempotencyKey = requireIdempotencyKey(idempotencyKey);
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(requestedAt, "requestedAt");
    }

    static String requireReason(String value) {
        if (value == null || value.isBlank() || value.trim().length() > 1000) {
            throw new IllegalArgumentException("reason must contain 1 to 1000 characters");
        }
        return value.trim();
    }

    static String requireIdempotencyKey(String value) {
        if (value == null || value.trim().length() < 8 || value.trim().length() > 128) {
            throw new IllegalArgumentException("Idempotency-Key must be between 8 and 128 characters");
        }
        return value.trim();
    }
}
