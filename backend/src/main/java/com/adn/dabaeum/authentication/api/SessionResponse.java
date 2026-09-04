package com.adn.dabaeum.authentication.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SessionResponse(
    UUID userId,
    String provider,
    List<SessionRole> roles,
    Instant expiresAt
) {

    public SessionResponse {
        roles = List.copyOf(roles);
    }

    public record SessionRole(String role, UUID institutionId) {
    }
}
