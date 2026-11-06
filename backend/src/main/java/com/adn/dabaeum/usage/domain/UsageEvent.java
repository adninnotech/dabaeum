package com.adn.dabaeum.usage.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * 원문 기록 한 건 (docs/specs/usage-statistics.md §7). (type, date, viewerKey, targetKey) 가 하루 1건의 키다.
 * targetKey: 방문은 빈 문자열, 검색은 정규화한 검색어, 배너는 배너 id.
 */
public record UsageEvent(
    UsageEventType type,
    LocalDate date,
    String viewerKey,
    String targetKey,
    VisitorSegment segment,
    boolean zeroResult,
    Instant at
) {

    /** 개인정보로 보여 저장하지 않은 검색어 자리 (§3.3). 검색 수에는 들고 순위에는 나오지 않는다. */
    public static final String MASKED_KEYWORD = "#";

    public UsageEvent {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(date, "date");
        Objects.requireNonNull(viewerKey, "viewerKey");
        Objects.requireNonNull(targetKey, "targetKey");
        Objects.requireNonNull(segment, "segment");
        Objects.requireNonNull(at, "at");
    }
}
