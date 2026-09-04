package com.adn.dabaeum.authentication.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

@JsonIgnoreProperties(ignoreUnknown = false)
public record SignupRequest(
    @NotBlank @Email @Size(max = 320) String email,
    @NotBlank @Size(min = 10) String password,
    @NotBlank @Size(max = 100) String name,
    @Size(max = 30) String phone,
    @PastOrPresent LocalDate birthDate
) {
}
