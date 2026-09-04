package com.adn.dabaeum.role.application;

import com.adn.dabaeum.role.domain.UserRoleAssignment;
import java.util.List;
import java.util.UUID;

public interface RoleApplicationService {

    List<UserRoleAssignment> list(UUID userId);

    UserRoleAssignment assign(AssignRoleCommand command);

    UserRoleAssignment revoke(UUID userId, UUID roleId);
}
