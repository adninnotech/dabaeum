package com.adn.dabaeum.inquiry.application;

import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import java.time.Instant;
import java.util.UUID;

public record ReplyInquiryCommand(
    UUID inquiryId,
    String content,
    AuthenticatedUserContext actor,
    Instant requestedAt
) {
}
