package com.adn.dabaeum.credential.application;

import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record IssueCredentialCommand(
    UUID completionId,
    Instant validUntil,
    String idempotencyKey,
    AuthenticatedUserContext actor,
    Instant requestedAt
) {

    public IssueCredentialCommand {
        Objects.requireNonNull(completionId, "completionId");
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(requestedAt, "requestedAt");
        if (idempotencyKey == null || idempotencyKey.trim().length() < 8
            || idempotencyKey.trim().length() > 128) {
            throw new IllegalArgumentException("Idempotency-Key must be between 8 and 128 characters");
        }
        idempotencyKey = idempotencyKey.trim();
    }
}
