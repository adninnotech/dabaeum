package com.adn.dabaeum.identity.domain;

import java.time.Instant;
import java.util.UUID;

public record UserIdentity(
    UUID id,
    UUID userId,
    IdentityProvider provider,
    String providerSubject,
    String externalDid,
    Instant verifiedAt,
    String metadata,
    Instant createdAt,
    Instant updatedAt
) {

    public UserIdentity {
        if (id == null) {
            throw new IllegalArgumentException("id is required");
        }
        if (userId == null) {
            throw new IllegalArgumentException("userId is required");
        }
        if (provider == null) {
            throw new IllegalArgumentException("provider is required");
        }
        if (providerSubject == null || providerSubject.isBlank()) {
            throw new IllegalArgumentException("providerSubject is required");
        }
        providerSubject = providerSubject.trim();
        if (externalDid != null) {
            if (externalDid.isBlank()) {
                throw new IllegalArgumentException("externalDid must not be blank");
            }
            externalDid = externalDid.trim();
        }
        if (createdAt == null) {
            throw new IllegalArgumentException("createdAt is required");
        }
        if (updatedAt == null) {
            throw new IllegalArgumentException("updatedAt is required");
        }
    }
}
