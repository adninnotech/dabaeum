package com.adn.dabaeum.authentication.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@JsonIgnoreProperties(ignoreUnknown = false)
public record LoginRequest(
    @NotBlank @Email @Size(max = 320) String email,
    @NotBlank String password
) {
}
