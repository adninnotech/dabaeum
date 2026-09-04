package com.adn.dabaeum.credential.application;

import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import java.util.Objects;
import java.util.UUID;

public record ListUserCredentialsQuery(
    UUID userId,
    int page,
    int size,
    String sort,
    AuthenticatedUserContext actor
) {

    public ListUserCredentialsQuery {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(actor, "actor");
        if (page < 0) {
            throw new IllegalArgumentException("page must not be negative");
        }
        if (size < 1 || size > 100) {
            throw new IllegalArgumentException("size must be between 1 and 100");
        }
        if (sort == null || sort.isBlank()) {
            throw new IllegalArgumentException("sort is required");
        }
        sort = sort.trim();
    }
}
