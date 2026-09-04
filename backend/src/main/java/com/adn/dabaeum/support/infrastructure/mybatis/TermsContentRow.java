package com.adn.dabaeum.support.infrastructure.mybatis;

import java.time.Instant;
import java.util.UUID;

public record TermsContentRow(
    UUID id,
    String type,
    String title,
    String body,
    String version,
    Instant createdAt,
    Instant updatedAt
) {
}
