package com.adn.dabaeum.support.application;

import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import com.adn.dabaeum.support.domain.NoticeAudience;
import com.adn.dabaeum.support.domain.NoticeStatus;
import java.time.Instant;
import java.util.UUID;

public record NoticeCommand(
    UUID noticeId,
    String title,
    String body,
    NoticeAudience audience,
    NoticeStatus status,
    AuthenticatedUserContext actor,
    Instant requestedAt
) {
}
