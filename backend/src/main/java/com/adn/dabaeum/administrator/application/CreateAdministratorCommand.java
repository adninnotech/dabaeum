package com.adn.dabaeum.administrator.application;

import com.adn.dabaeum.role.domain.UserRole;
import java.util.UUID;

public record CreateAdministratorCommand(String name, String email, UserRole role, UUID institutionId) {
}
