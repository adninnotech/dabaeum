package com.adn.dabaeum.common.security;

import java.util.UUID;

public record AuthenticatedRole(String role, UUID institutionId) {

    public AuthenticatedRole {
        if (role == null || role.isBlank()) {
            throw new IllegalArgumentException("role is required");
        }
        role = role.trim();
    }
}
