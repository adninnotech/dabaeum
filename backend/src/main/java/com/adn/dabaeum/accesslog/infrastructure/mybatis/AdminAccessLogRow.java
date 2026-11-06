package com.adn.dabaeum.accesslog.infrastructure.mybatis;

import java.time.Instant;
import java.util.UUID;

/** 쓰기용 행. uuid[] 는 문자열 배열로 넘기고 SQL 에서 바꾼다. */
public record AdminAccessLogRow(
    UUID id,
    UUID actorId,
    String actorRole,
    String action,
    String endpoint,
    String[] targetUserIds,
    int resultCount,
    int statusCode,
    String ipAddress,
    String userAgent,
    UUID requestId,
    Instant createdAt
) {
}
