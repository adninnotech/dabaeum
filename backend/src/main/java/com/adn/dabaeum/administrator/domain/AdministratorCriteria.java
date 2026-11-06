package com.adn.dabaeum.administrator.domain;

import com.adn.dabaeum.role.domain.UserRole;
import com.adn.dabaeum.user.domain.UserStatus;
import java.time.Instant;

/**
 * 관리자 검색 조건. role 이 null 이면 두 관리자 역할 모두. keyword 는 {@code KeywordQuery.normalize} 를 거친 값.
 * now 는 지난 잠금을 null 로 돌려주는 기준 시각이다.
 */
public record AdministratorCriteria(
    UserRole role,
    UserStatus status,
    String keyword,
    Instant now,
    int offset,
    int limit
) {
}
