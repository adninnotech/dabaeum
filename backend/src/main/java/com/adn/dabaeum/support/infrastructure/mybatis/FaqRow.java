package com.adn.dabaeum.support.infrastructure.mybatis;

import java.time.Instant;
import java.util.UUID;

public record FaqRow(
    UUID id,
    String question,
    String answer,
    Integer sortOrder,
    Instant createdAt,
    Instant updatedAt
) {
}
