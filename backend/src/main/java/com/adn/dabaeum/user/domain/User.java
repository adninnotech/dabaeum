package com.adn.dabaeum.user.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record User(
    UUID id,
    String name,
    String email,
    String phone,
    LocalDate birthDate,
    UserStatus status,
    Instant withdrawnAt,
    Instant createdAt,
    Instant updatedAt,
    String career,
    String introduction,
    UUID profileImageId
) {

    /** 프로필 확장 필드가 없는 기본 사용자. */
    public User(
        UUID id,
        String name,
        String email,
        String phone,
        LocalDate birthDate,
        UserStatus status,
        Instant withdrawnAt,
        Instant createdAt,
        Instant updatedAt
    ) {
        this(id, name, email, phone, birthDate, status, withdrawnAt, createdAt, updatedAt,
            null, null, null);
    }
}
