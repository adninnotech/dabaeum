package com.adn.dabaeum.user.api;

import com.adn.dabaeum.user.domain.UserStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record UserResponse(
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
}
