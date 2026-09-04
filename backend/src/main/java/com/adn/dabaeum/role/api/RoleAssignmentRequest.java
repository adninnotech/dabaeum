package com.adn.dabaeum.role.api;

import com.adn.dabaeum.role.domain.UserRole;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = false)
public record RoleAssignmentRequest(
    @NotNull UserRole role,
    UUID institutionId
) {
}
