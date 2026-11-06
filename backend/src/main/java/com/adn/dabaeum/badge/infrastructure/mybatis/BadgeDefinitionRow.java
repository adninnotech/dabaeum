package com.adn.dabaeum.badge.infrastructure.mybatis;

import java.time.Instant;
import java.util.UUID;

/** rule_params 는 JSON 문자열로 주고받는다. */
public record BadgeDefinitionRow(
    UUID id,
    String code,
    String name,
    String description,
    UUID imageFileId,
    String ruleType,
    String ruleParams,
    boolean active,
    Instant createdAt,
    Instant updatedAt
) {
}
