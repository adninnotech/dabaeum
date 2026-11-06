package com.adn.dabaeum.role.api;

import com.adn.dabaeum.role.domain.UserRole;
import java.util.UUID;

public record MyInstitutionResponse(
    UUID institutionId,
    String name,
    UserRole role
) {
}
