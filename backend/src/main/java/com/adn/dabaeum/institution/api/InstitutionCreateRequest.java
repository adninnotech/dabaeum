package com.adn.dabaeum.institution.api;

import com.adn.dabaeum.institution.domain.InstitutionStatus;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@JsonIgnoreProperties(ignoreUnknown = false)
public record InstitutionCreateRequest(
    @NotBlank @Size(max = 50) String institutionCode,
    @NotBlank @Size(max = 200) String name,
    @Size(max = 20) String businessNumber,
    @Size(max = 100) String representativeName,
    String address,
    @Size(max = 30) String contactPhone,
    @Email @Size(max = 320) String contactEmail,
    InstitutionStatus status
) {
}
