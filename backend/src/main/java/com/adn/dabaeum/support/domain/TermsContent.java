package com.adn.dabaeum.support.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record TermsContent(
    UUID id,
    TermsType type,
    String title,
    String body,
    String version,
    Instant createdAt,
    Instant updatedAt
) {

    public TermsContent {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        if (title == null || title.isBlank() || title.length() > 200) {
            throw new IllegalArgumentException("title must be 1..200 characters");
        }
        if (body == null || body.isBlank()) {
            throw new IllegalArgumentException("body must not be blank");
        }
        if (version == null || version.isBlank() || version.length() > 30) {
            throw new IllegalArgumentException("version must be 1..30 characters");
        }
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }
}
