package com.adn.dabaeum.role.domain;

import java.time.Instant;
import java.util.UUID;

public record UserRoleAssignment(
    UUID id,
    UUID userId,
    UUID institutionId,
    UserRole role,
    Instant createdAt
) {

    public UserRoleAssignment {
        if (id == null) {
            throw new IllegalArgumentException("id is required");
        }
        if (userId == null) {
            throw new IllegalArgumentException("userId is required");
        }
        if (role == null) {
            throw new IllegalArgumentException("role is required");
        }
        if (createdAt == null) {
            throw new IllegalArgumentException("createdAt is required");
        }
        if (role == UserRole.PLATFORM_ADMIN && institutionId != null) {
            throw new IllegalArgumentException(
                "PLATFORM_ADMIN cannot have an institution"
            );
        }
        if ((role == UserRole.INSTITUTION_ADMIN || role == UserRole.INSTRUCTOR)
            && institutionId == null) {
            throw new IllegalArgumentException(
                "institution is required for scoped roles"
            );
        }
    }
}
