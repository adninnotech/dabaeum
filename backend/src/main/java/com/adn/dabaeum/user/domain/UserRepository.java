package com.adn.dabaeum.user.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository {

    void save(User user);

    Optional<User> findById(UUID id);

    List<User> findPage(UserPageCriteria criteria);

    long count(UserStatus status);

    boolean updateProfile(User user);

    boolean updateStatus(
        UUID id,
        UserStatus status,
        Instant withdrawnAt,
        Instant updatedAt
    );

    /** 이 시각보다 이르거나 같은 발급 시각의 토큰은 거부한다. 무효화 이력이 없으면 empty. */
    Optional<Instant> findAuthInvalidatedAt(UUID id);

    /** 비밀번호 재설정·계정 정지 등으로 기존 토큰을 즉시 무효화한다. 사용자가 없으면 false. */
    boolean invalidateTokens(UUID id, Instant at);
}
