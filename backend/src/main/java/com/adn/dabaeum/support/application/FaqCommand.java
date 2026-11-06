package com.adn.dabaeum.support.application;

import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import java.time.Instant;
import java.util.UUID;

public record FaqCommand(
    /** null 이면 생성이다. */
    UUID faqId,
    String question,
    String answer,
    int sortOrder,
    AuthenticatedUserContext actor,
    Instant requestedAt
) {
}
