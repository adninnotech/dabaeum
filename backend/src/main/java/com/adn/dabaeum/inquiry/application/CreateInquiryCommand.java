package com.adn.dabaeum.inquiry.application;

import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import java.time.Instant;
import java.util.UUID;

public record CreateInquiryCommand(
    String title,
    String content,
    UUID courseId,
    AuthenticatedUserContext actor,
    Instant requestedAt
) {
}
