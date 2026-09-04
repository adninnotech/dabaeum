package com.adn.dabaeum.credential.application;

import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record ReissueCredentialCommand(
    UUID credentialId,
    String reason,
    Instant validUntil,
    String idempotencyKey,
    AuthenticatedUserContext actor,
    Instant requestedAt
) {
    public ReissueCredentialCommand {
        Objects.requireNonNull(credentialId, "credentialId");
        reason = RevokeCredentialCommand.requireReason(reason);
        idempotencyKey = RevokeCredentialCommand.requireIdempotencyKey(idempotencyKey);
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(requestedAt, "requestedAt");
    }
}
