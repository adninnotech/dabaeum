package com.adn.dabaeum.role.api;

import com.adn.dabaeum.role.domain.UserRole;
import java.time.Instant;
import java.util.UUID;

public record RoleResponse(
    UUID id,
    UserRole role,
    UUID institutionId,
    Instant createdAt
) {
}
