package com.adn.dabaeum.publiccourse.domain;

import java.time.LocalTime;

/**
 * 수업 시작 시각으로 나눈 시간대 (설계서 §5.1). 구간은 [from, before) 이며 null 은 열린 끝이다.
 * 시작 시각이 없는 강좌는 어느 시간대에도 들지 않는다.
 */
public enum PublicCourseTimeOfDay {
    MORNING(null, LocalTime.NOON),
    AFTERNOON(LocalTime.NOON, LocalTime.of(18, 0)),
    EVENING(LocalTime.of(18, 0), null);

    private final LocalTime from;
    private final LocalTime before;

    PublicCourseTimeOfDay(LocalTime from, LocalTime before) {
        this.from = from;
        this.before = before;
    }

    public LocalTime from() {
        return from;
    }

    public LocalTime before() {
        return before;
    }
}
