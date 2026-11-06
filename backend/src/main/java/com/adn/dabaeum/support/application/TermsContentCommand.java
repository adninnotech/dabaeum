package com.adn.dabaeum.support.application;

import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import com.adn.dabaeum.support.domain.TermsType;
import java.time.Instant;

public record TermsContentCommand(
    TermsType type,
    String title,
    String body,
    String version,
    AuthenticatedUserContext actor,
    Instant requestedAt
) {
}
