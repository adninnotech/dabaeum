package com.adn.dabaeum.authentication.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@JsonIgnoreProperties(ignoreUnknown = false)
public record ChangePasswordRequest(
    @NotBlank @Size(max = 128) String currentPassword,
    @NotBlank @Size(min = 10, max = 128) String newPassword
) {
}
