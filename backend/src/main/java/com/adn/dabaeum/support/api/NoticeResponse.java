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
    /** 작성자 이름. 작성자 행을 못 찾으면 null 이다. */
    String authorName,
    Instant createdAt,
    Instant updatedAt
) {
}
