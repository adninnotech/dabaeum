package com.adn.dabaeum.notification.api;

import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(
    UUID id,
    String title,
    String body,
    boolean read,
    Instant createdAt
) {
}
