package com.adn.dabaeum.badge.api;

import java.util.UUID;

public record BadgeIssueRequest(
    UUID definitionId,
    UUID courseId
) {
}
