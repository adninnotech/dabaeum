package com.adn.dabaeum.badge.application;

import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import java.time.Instant;
import java.util.UUID;

public record IssueLearningBadgeCommand(
    UUID credentialId,
    String badgeType,
    String badgeName,
    UUID courseId,
    String idempotencyKey,
    AuthenticatedUserContext actor,
    Instant requestedAt
) {
}
