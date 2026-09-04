package com.adn.dabaeum.role.application;

import com.adn.dabaeum.role.domain.UserRole;
import java.util.UUID;

public record AssignRoleCommand(
    UUID userId,
    UserRole role,
    UUID institutionId
) {
}
