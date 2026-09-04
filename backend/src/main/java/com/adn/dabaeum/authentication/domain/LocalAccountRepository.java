package com.adn.dabaeum.authentication.domain;

import java.util.Optional;

public interface LocalAccountRepository {

    void save(LocalAccountCredential credential);

    Optional<LocalAccountCredential> findByNormalizedEmail(
        String normalizedEmail
    );

    Optional<LocalAccountCredential> findByUserId(java.util.UUID userId);

    boolean updatePasswordHash(
        java.util.UUID userId, String passwordHash, java.time.Instant updatedAt);
}
