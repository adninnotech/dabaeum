package com.adn.dabaeum.support.infrastructure.mybatis;

import java.time.Instant;
import java.util.UUID;

public record NoticeRow(
    UUID id,
    String title,
    String body,
    String audience,
    String status,
    Instant publishedAt,
    UUID createdBy,
    Instant createdAt,
    Instant updatedAt
) {
}
