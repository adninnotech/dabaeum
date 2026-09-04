package com.adn.dabaeum.identity.infrastructure.mybatis;

import java.time.Instant;
import java.util.UUID;

public record UserIdentityRow(
    UUID id,
    UUID userId,
    String provider,
    String providerSubject,
    String externalDid,
    Instant verifiedAt,
    String metadata,
    Instant createdAt,
    Instant updatedAt
) {
}
