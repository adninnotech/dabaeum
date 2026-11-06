package com.adn.dabaeum.administrator.domain;

import com.adn.dabaeum.role.domain.RoleSummary;
import com.adn.dabaeum.user.domain.UserStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** 관리자 목록의 한 행 (설계서 §10.1). lockedUntil 은 지금 잠겨 있을 때만 값이 있다. */
public record Administrator(
    UUID userId,
    String name,
    String email,
    UserStatus status,
    List<RoleSummary> roles,
    Instant lastLoginAt,
    Instant lockedUntil,
    Instant passwordChangedAt,
    Instant createdAt
) {

    public Administrator {
        roles = List.copyOf(roles);
    }

    public Administrator withRoles(List<RoleSummary> value) {
        return new Administrator(userId, name, email, status, value, lastLoginAt, lockedUntil,
            passwordChangedAt, createdAt);
    }
}
