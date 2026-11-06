package com.adn.dabaeum.authentication.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = false)
public record DadaeguSignupRequest(
    @NotBlank @Size(max = 128) String registrationToken,
    @Size(max = 10) List<String> interestCodes,
    Boolean agreedToInterest,
    @NotNull Boolean agreedToUsage,
    @NotNull Boolean agreedToPrivacy,
    Boolean agreedToMarketing
) {
}
