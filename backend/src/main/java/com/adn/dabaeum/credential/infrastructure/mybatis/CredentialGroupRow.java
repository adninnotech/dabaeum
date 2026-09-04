package com.adn.dabaeum.credential.infrastructure.mybatis;

import java.time.Instant;
import java.util.UUID;

public record CredentialGroupRow(
    UUID id,
    UUID completionId,
    Instant createdAt
) {
}
