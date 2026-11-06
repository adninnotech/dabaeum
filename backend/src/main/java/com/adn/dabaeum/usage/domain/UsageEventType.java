package com.adn.dabaeum.usage.domain;

/** 이용 통계가 직접 남기는 기록 (docs/specs/usage-statistics.md §4). 강좌 조회·수강 신청은 기존 테이블을 집계한다. */
public enum UsageEventType {
    VISIT,
    SEARCH,
    BANNER_CLICK
}
