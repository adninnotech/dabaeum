package com.adn.dabaeum.authentication.domain;

import java.time.Instant;
import java.util.UUID;

public record LocalAccountCredential(
    UUID identityId,
    UUID userId,
    String normalizedEmail,
    String passwordHash,
    Instant createdAt,
    Instant updatedAt
) {

    public LocalAccountCredential {
        if (identityId == null || userId == null) {
            throw new IllegalArgumentException("identityId and userId are required");
        }
        if (normalizedEmail == null || normalizedEmail.isBlank()) {
            throw new IllegalArgumentException("normalizedEmail is required");
        }
        if (passwordHash == null || passwordHash.isBlank()) {
            throw new IllegalArgumentException("passwordHash is required");
        }
        if (createdAt == null || updatedAt == null) {
            throw new IllegalArgumentException("timestamps are required");
        }
    }
}
