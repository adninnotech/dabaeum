package com.adn.dabaeum.administrator.api;

import com.adn.dabaeum.administrator.application.CreateAdministratorCommand;
import com.adn.dabaeum.role.domain.UserRole;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.adn.dabaeum.user.domain.EmailAddress;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = false)
public record AdministratorCreateRequest(
    @NotBlank @Size(max = 100) String name,
    @NotBlank @Email(regexp = EmailAddress.PATTERN) @Size(max = 320) String email,
    @NotNull UserRole role,
    UUID institutionId
) {

    CreateAdministratorCommand toCommand() {
        return new CreateAdministratorCommand(name, email, role, institutionId);
    }
}
