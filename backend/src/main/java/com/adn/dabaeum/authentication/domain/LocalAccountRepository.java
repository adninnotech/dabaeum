package com.adn.dabaeum.authentication.domain;

import java.util.Optional;

public interface LocalAccountRepository {

    void save(LocalAccountCredential credential);

    Optional<LocalAccountCredential> findByNormalizedEmail(
        String normalizedEmail
    );

    Optional<LocalAccountCredential> findByUserId(java.util.UUID userId);

    boolean updatePasswordHash(
        java.util.UUID userId, String passwordHash, java.time.Instant updatedAt);

    /** LOCAL 비밀번호가 없는 계정(다대구 전용)이면 비어 있다. */
    Optional<PasswordAge> findPasswordAge(java.util.UUID userId);

    /** 관리자가 발급한 임시 비밀번호로 표시한다. 본인이 바꾸면 updatePasswordHash 가 다시 채운다. */
    boolean markPasswordTemporary(java.util.UUID userId);
}
