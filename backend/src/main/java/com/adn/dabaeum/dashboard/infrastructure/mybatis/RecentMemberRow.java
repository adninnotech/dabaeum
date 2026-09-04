package com.adn.dabaeum.dashboard.infrastructure.mybatis;

import java.time.Instant;
import java.util.UUID;

public record RecentMemberRow(
    UUID userId,
    String name,
    String role,
    Instant createdAt
) {
}
