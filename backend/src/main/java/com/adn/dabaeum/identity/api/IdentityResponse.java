package com.adn.dabaeum.identity.api;

import com.adn.dabaeum.identity.domain.IdentityProvider;
import java.time.Instant;
import java.util.UUID;

public record IdentityResponse(
    UUID id,
    IdentityProvider provider,
    Instant verifiedAt,
    Instant createdAt,
    Instant updatedAt
) {
}
