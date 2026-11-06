package com.adn.dabaeum.user.domain;

import java.util.Objects;

/**
 * 관리자 회원 목록(B-4)의 한 행. 사용자와 목록 전용 집계 두 개를 묶는다 —
 * enrollmentCount(승인된 수강 건수)·courseCount(강사로 배정된 과정 수)는
 * 목록 조회에서만 채우고 단건 조회(findById)에는 없다.
 */
public record UserListItem(User user, long enrollmentCount, long courseCount) {

    public UserListItem {
        Objects.requireNonNull(user, "user");
    }
}
