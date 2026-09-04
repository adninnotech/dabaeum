package com.adn.dabaeum.badge.application;

import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import java.util.UUID;

public record ListUserBadgesQuery(
    UUID userId,
    int page,
    int size,
    String sort,
    AuthenticatedUserContext actor
) {
}
