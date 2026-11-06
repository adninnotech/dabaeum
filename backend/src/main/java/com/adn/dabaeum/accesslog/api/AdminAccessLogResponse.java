package com.adn.dabaeum.accesslog.api;

import com.adn.dabaeum.accesslog.domain.AccessLogAction;
import com.adn.dabaeum.accesslog.domain.AdminAccessLogEntry;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AdminAccessLogResponse(
    UUID id,
    UUID actorId,
    String actorName,
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

    static AdminAccessLogResponse from(AdminAccessLogEntry entry) {
        var log = entry.log();
        return new AdminAccessLogResponse(
            log.id(), log.actorId(), entry.actorName(), log.actorRole(), log.action(), log.endpoint(),
            log.targetUserIds(), log.resultCount(), log.statusCode(), log.ipAddress(), log.userAgent(),
            log.requestId(), log.createdAt());
    }
}
