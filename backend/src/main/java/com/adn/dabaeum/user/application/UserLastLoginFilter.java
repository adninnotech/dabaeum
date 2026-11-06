package com.adn.dabaeum.user.application;

/**
 * 관리자 학생 목록의 최근 접속 필터. 기준 시각은 요청 시점이다.
 * OVER_90 은 기록이 있는 사람만, 기록이 없는 사람은 NEVER 로 따로 찾는다.
 */
public enum UserLastLoginFilter {
    WITHIN_7,
    WITHIN_30,
    OVER_90,
    NEVER
}
