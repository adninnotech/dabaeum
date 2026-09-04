package com.adn.dabaeum.user.api;

import com.adn.dabaeum.user.domain.UserStatus;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

@JsonIgnoreProperties(ignoreUnknown = false)
public record UserCreateRequest(
    @NotBlank @Size(max = 100) String name,
    @Email @Size(max = 320) String email,
    @Size(max = 30) String phone,
    LocalDate birthDate,
    UserStatus status
) {
}
