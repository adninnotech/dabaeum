package com.adn.dabaeum.administrator.api;

import com.adn.dabaeum.administrator.domain.Administrator;
import com.adn.dabaeum.role.domain.RoleSummary;
import com.adn.dabaeum.user.domain.UserStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AdministratorResponse(
    UUID userId,
    String name,
    String email,
    UserStatus status,
    List<RoleSummary> roles,
    Instant lastLoginAt,
    Instant lockedUntil,
    Instant passwordChangedAt,
    Instant createdAt
) {

    static AdministratorResponse from(Administrator administrator) {
        return new AdministratorResponse(
            administrator.userId(), administrator.name(), administrator.email(),
            administrator.status(), administrator.roles(), administrator.lastLoginAt(),
            administrator.lockedUntil(), administrator.passwordChangedAt(), administrator.createdAt());
    }
}
