package com.adn.dabaeum.common.security;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public record AuthenticatedUserPrincipal(
    UUID userId,
    String provider,
    Set<String> roles
) {

    public AuthenticatedUserPrincipal(UUID userId, Set<String> roles) {
        this(userId, "LOCAL", roles);
    }

    public AuthenticatedUserPrincipal {
        if (userId == null) {
            throw new IllegalArgumentException("userId is required");
        }
        if (provider == null || provider.isBlank()) {
            throw new IllegalArgumentException("provider is required");
        }
        provider = provider.trim();
        roles = Set.copyOf(Objects.requireNonNull(roles, "roles"));
    }

    public AuthenticatedUserContext toContext() {
        return new AuthenticatedUserContext(
            userId,
            provider,
            roles.stream()
                .map(role -> new AuthenticatedRole(role, null))
                .collect(Collectors.toUnmodifiableSet())
        );
    }
}
