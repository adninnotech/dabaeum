package com.adn.dabaeum.common.security;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record AuthenticatedUserContext(
    UUID userId,
    String provider,
    Set<AuthenticatedRole> roles
) {

    public AuthenticatedUserContext {
        if (userId == null) {
            throw new IllegalArgumentException("userId is required");
        }
        if (provider == null || provider.isBlank()) {
            throw new IllegalArgumentException("provider is required");
        }
        provider = provider.trim();
        roles = Set.copyOf(Objects.requireNonNull(roles, "roles"));
    }
}
