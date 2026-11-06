package com.adn.dabaeum.accesslog.domain;

import java.time.Instant;
import java.util.UUID;

/** 접속기록 검색 조건. from 포함, toExclusive 미포함. null 이면 그 조건 없음. */
public record AdminAccessLogCriteria(
    UUID actorId,
    AccessLogAction action,
    Instant from,
    Instant toExclusive,
    int offset,
    int limit
) {
}
