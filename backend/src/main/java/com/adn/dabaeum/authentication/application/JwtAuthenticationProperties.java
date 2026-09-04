package com.adn.dabaeum.authentication.application;

import java.util.Map;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "dabaeum.security.production.jwt")
public record JwtAuthenticationProperties(
    String issuer,
    String audience,
    Map<String, String> signingKeys
) {

    public JwtAuthenticationProperties {
        if (issuer == null || issuer.isBlank()) {
            throw new IllegalArgumentException("issuer is required");
        }
        if (audience == null || audience.isBlank()) {
            throw new IllegalArgumentException("audience is required");
        }
        issuer = issuer.trim();
        audience = audience.trim();
        signingKeys = Map.copyOf(Objects.requireNonNull(
            signingKeys,
            "signingKeys"
        ));
        if (signingKeys.isEmpty()) {
            throw new IllegalArgumentException("signingKeys are required");
        }
    }
}
