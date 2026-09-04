package com.adn.dabaeum.support.api;

import java.time.Instant;
import java.util.UUID;

public record NoticeSummaryResponse(
    UUID id,
    String title,
    Instant publishedAt
) {
}
