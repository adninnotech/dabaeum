package com.adn.dabaeum.usage.domain;

/** 통계 화면의 지표 (docs/specs/usage-statistics.md §2). 모두 하루 한 사람 1회로 센다(신청은 1건). */
public enum UsageMetric {
    VISIT,
    SEARCH,
    COURSE_VIEW,
    ENROLLMENT,
    BANNER_CLICK
}
