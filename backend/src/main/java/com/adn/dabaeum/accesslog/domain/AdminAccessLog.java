package com.adn.dabaeum.accesslog.domain;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** 관리자 접속기록 한 줄 (설계서 §10.2). targetUserIds 가 null 이면 대상이 1000명 초과이거나 응답에 id 가 없는 목록이다. */
public record AdminAccessLog(
    UUID id,
    UUID actorId,
    String actorRole,
    AccessLogAction action,
    String endpoint,
    List<UUID> targetUserIds,
    int resultCount,
    int statusCode,
    String ipAddress,
    String userAgent,
    UUID requestId,
    Instant createdAt
) {

    public AdminAccessLog {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(actorId, "actorId");
        Objects.requireNonNull(actorRole, "actorRole");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(endpoint, "endpoint");
        Objects.requireNonNull(createdAt, "createdAt");
        targetUserIds = targetUserIds == null ? null : List.copyOf(targetUserIds);
        if (resultCount < 0) {
            throw new IllegalArgumentException("resultCount must not be negative");
        }
    }
}
