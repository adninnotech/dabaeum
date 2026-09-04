package com.adn.dabaeum.common.security;

import java.time.Instant;

public record AuthenticatedTokenDetails(
    Instant expiresAt,
    AuthenticatedUserContext context
) {

    public AuthenticatedTokenDetails(Instant expiresAt) {
        this(expiresAt, null);
    }

    public AuthenticatedTokenDetails {
        if (expiresAt == null) {
            throw new IllegalArgumentException("expiresAt is required");
        }
    }
}
