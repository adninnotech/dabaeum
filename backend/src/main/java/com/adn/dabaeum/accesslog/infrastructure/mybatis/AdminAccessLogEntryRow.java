package com.adn.dabaeum.accesslog.infrastructure.mybatis;

import java.time.Instant;
import java.util.UUID;

/** 읽기용 행. 대상 id 는 쉼표로 이은 문자열, IP 는 host() 문자열로 받는다. */
public record AdminAccessLogEntryRow(
    UUID id,
    UUID actorId,
    String actorName,
    String actorRole,
    String action,
    String endpoint,
    String targetUserIdsCsv,
    int resultCount,
    int statusCode,
    String ipAddress,
    String userAgent,
    UUID requestId,
    Instant createdAt
) {
}
