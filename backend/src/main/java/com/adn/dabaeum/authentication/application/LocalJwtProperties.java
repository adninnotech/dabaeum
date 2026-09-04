package com.adn.dabaeum.authentication.application;

import java.time.Duration;
import java.util.Base64;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "dabaeum.security.local-account.jwt")
public record LocalJwtProperties(
    String issuer,
    String audience,
    String keyId,
    String signingKey,
    Duration accessTokenTtl
) {

    public LocalJwtProperties {
        issuer = required(issuer, "issuer");
        audience = required(audience, "audience");
        keyId = required(keyId, "keyId");
        signingKey = required(signingKey, "signingKey");
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(signingKey);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("signingKey must be Base64", exception);
        }
        if (decoded.length < 32) {
            throw new IllegalArgumentException("signingKey must be at least 32 bytes");
        }
        if (accessTokenTtl == null || accessTokenTtl.isZero()
            || accessTokenTtl.isNegative()) {
            throw new IllegalArgumentException("accessTokenTtl must be positive");
        }
    }

    public byte[] decodedSigningKey() {
        return Base64.getDecoder().decode(signingKey);
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }
}
