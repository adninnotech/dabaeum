package com.adn.dabaeum.notification.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record Notification(
    UUID id,
    UUID userId,
    String title,
    String body,
    Instant readAt,
    Instant createdAt
) {

    public Notification {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(userId, "userId");
        if (title == null || title.isBlank() || title.length() > 200) {
            throw new IllegalArgumentException("title must be 1..200 characters");
        }
        if (body != null && body.length() > 2000) {
            throw new IllegalArgumentException("body must not exceed 2000 characters");
        }
        Objects.requireNonNull(createdAt, "createdAt");
    }
}
