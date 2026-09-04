package com.adn.dabaeum.support.api;

import com.adn.dabaeum.support.domain.NoticeAudience;
import com.adn.dabaeum.support.domain.NoticeStatus;
import java.time.Instant;
import java.util.UUID;

public record NoticeResponse(
    UUID id,
    String title,
    String body,
    NoticeAudience audience,
    NoticeStatus status,
    Instant publishedAt,
    Instant createdAt,
    Instant updatedAt
) {
}
