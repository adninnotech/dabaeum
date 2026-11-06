package com.adn.dabaeum.badge.application;

import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import java.time.Instant;
import java.util.UUID;

/** 수동 발급: 수료증에 정의 배지를 붙인다(설계서 §11.2). 배지 종류·이름은 정의에서 온다. */
public record IssueLearningBadgeCommand(
    UUID credentialId,
    UUID definitionId,
    UUID courseId,
    String idempotencyKey,
    AuthenticatedUserContext actor,
    Instant requestedAt
) {
}
