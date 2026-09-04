package com.adn.dabaeum.credential.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record CredentialGroup(
    UUID id,
    UUID completionId,
    Instant createdAt
) {

    public CredentialGroup {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(completionId, "completionId");
        Objects.requireNonNull(createdAt, "createdAt");
    }
}
