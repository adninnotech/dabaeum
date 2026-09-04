package com.adn.dabaeum.authentication.application;

import java.time.Instant;

public record IssuedAccessToken(String value, Instant expiresAt) {

    public IssuedAccessToken {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("value is required");
        }
        if (expiresAt == null) {
            throw new IllegalArgumentException("expiresAt is required");
        }
    }
}
