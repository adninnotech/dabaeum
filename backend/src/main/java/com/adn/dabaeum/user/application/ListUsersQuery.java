package com.adn.dabaeum.user.application;

import com.adn.dabaeum.role.domain.UserRole;
import com.adn.dabaeum.user.domain.Gender;
import com.adn.dabaeum.user.domain.UserStatus;
import java.time.LocalDate;

/** createdFrom·createdTo 는 서울 날짜이며 양 끝을 포함한다. */
public record ListUsersQuery(
    int page,
    int size,
    String sort,
    UserStatus status,
    String keyword,
    UserRole role,
    Gender gender,
    Boolean hasEnrollment,
    UserLastLoginFilter lastLogin,
    LocalDate createdFrom,
    LocalDate createdTo
) {
}
