package com.adn.dabaeum.identity.api;

import com.adn.dabaeum.identity.domain.IdentityProvider;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@JsonIgnoreProperties(ignoreUnknown = false)
public record IdentityLinkRequest(
    @NotNull IdentityProvider provider,
    @NotBlank @Size(max = 255) String providerSubject,
    @Size(max = 512) String externalDid,
    @NotNull Boolean verified
) {
}
