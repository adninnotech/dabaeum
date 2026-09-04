package com.adn.dabaeum.user.application;

import com.adn.dabaeum.user.domain.UserStatus;

public record ListUsersQuery(
    int page,
    int size,
    String sort,
    UserStatus status
) {
}
