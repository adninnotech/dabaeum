package com.adn.dabaeum.role.infrastructure.mybatis;

import java.time.Instant;
import java.util.UUID;

public record UserRoleRow(
    UUID id,
    UUID userId,
    UUID institutionId,
    String role,
    Instant createdAt
) {
}
