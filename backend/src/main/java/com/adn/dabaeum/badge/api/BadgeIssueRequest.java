package com.adn.dabaeum.badge.api;

import java.util.UUID;

public record BadgeIssueRequest(
    String badgeType,
    String badgeName,
    UUID courseId
) {
}
