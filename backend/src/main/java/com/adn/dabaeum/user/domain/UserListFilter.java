package com.adn.dabaeum.user.domain;

import com.adn.dabaeum.role.domain.UserRole;
import java.time.Instant;

/**
 * 회원 목록의 필터 조건. 목록(findPage)과 합계(count)가 같은 값을 받아 같은 SQL 조각으로 거른다.
 * 시각 경계는 모두 서비스가 계산해 넘긴다 — From 은 이상, Before 는 미만이다.
 * lastLoginNever 는 접속 기록(last_login_at)이 없는 사람만 남긴다.
 */
public record UserListFilter(
    UserStatus status,
    String keyword,
    UserRole role,
    Gender gender,
    Boolean hasEnrollment,
    Instant lastLoginFrom,
    Instant lastLoginBefore,
    boolean lastLoginNever,
    Instant createdFrom,
    Instant createdBefore
) {

    /** 기본 세 가지 조건만 쓰는 목록용. */
    public static UserListFilter basic(UserStatus status, String keyword, UserRole role) {
        return new UserListFilter(
            status, keyword, role, null, null, null, null, false, null, null);
    }
}
