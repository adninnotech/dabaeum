package com.adn.dabaeum.notification.infrastructure.mybatis;

import java.time.Instant;
import java.util.UUID;

public record NotificationRow(
    UUID id,
    UUID userId,
    String title,
    String body,
    Instant readAt,
    Instant createdAt
) {
}
