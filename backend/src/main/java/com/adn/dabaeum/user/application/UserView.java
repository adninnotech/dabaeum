package com.adn.dabaeum.user.application;

import com.adn.dabaeum.role.domain.RoleSummary;
import com.adn.dabaeum.user.domain.User;
import java.util.List;
import java.util.Objects;

/**
 * 사용자 + 그 사용자의 역할. 응답의 roles 는 필수라 사용자를 읽는 모든 경로가 이 모양을 준다.
 * 역할이 없으면 {@code null} 이 아니라 빈 목록이다.
 *
 * enrollmentCount·courseCount 는 관리자 회원 목록에서만 채운다 — 그 외 조회(get·getMe)는
 * 2-인자 생성자를 써서 항상 {@code null} 이고, UserApiMapper 가 그 null 을 응답에서 지운다.
 */
public record UserView(User user, List<RoleSummary> roles, Long enrollmentCount, Long courseCount) {

    public UserView {
        Objects.requireNonNull(user, "user");
        roles = List.copyOf(roles); // null 이면 여기서 터진다 — 문서의 약속을 구조로 보장한다
    }

    public UserView(User user, List<RoleSummary> roles) {
        this(user, roles, null, null);
    }
}
