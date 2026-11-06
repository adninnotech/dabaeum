package com.adn.dabaeum.statistics.domain;

/** 수강 통계 축 (설계서 §9). 값마다 정해진 SQL 조각을 고른다 — 입력으로 SQL 을 조립하지 않는다. */
public enum EnrollmentStatGroup {
    CONTENT,
    YEAR,
    MONTH,
    AGE_BAND,
    /** 현재 회원 정보의 성별. 없으면 UNKNOWN (docs/specs/usage-statistics.md §6.5). */
    GENDER
}
