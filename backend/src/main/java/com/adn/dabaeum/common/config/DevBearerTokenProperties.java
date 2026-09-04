package com.adn.dabaeum.common.config;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "dabaeum.security.dev")
public record DevBearerTokenProperties(String bearerToken) {

    public boolean matches(String candidate) {
        if (bearerToken == null || bearerToken.isBlank()
            || candidate == null || candidate.isBlank()) {
            return false;
        }

        return MessageDigest.isEqual(
            bearerToken.getBytes(StandardCharsets.UTF_8),
            candidate.getBytes(StandardCharsets.UTF_8)
        );
    }
}
