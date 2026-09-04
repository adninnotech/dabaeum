package com.adn.dabaeum.user.domain;

public record UserPageCriteria(
    int offset,
    int limit,
    UserSort sort,
    UserSortDirection direction,
    UserStatus status
) {
}
