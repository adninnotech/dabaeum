package com.adn.dabaeum.badge.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** 배지 정의 (설계서 §11.1). code 는 바꿀 수 없는 식별자다. */
public record BadgeDefinition(
    UUID id,
    String code,
    String name,
    String description,
    UUID imageFileId,
    BadgeRule rule,
    boolean active,
    Instant createdAt,
    Instant updatedAt
) {

    public static final String CODE_PATTERN = "^[A-Z0-9_]{2,50}$";

    public BadgeDefinition {
        Objects.requireNonNull(id, "id");
        if (code == null || !code.matches(CODE_PATTERN)) {
            throw new IllegalArgumentException("code must be 2-50 upper-case letters, digits or underscores");
        }
        if (name == null || name.isBlank() || name.length() > 100) {
            throw new IllegalArgumentException("name must be 1-100 characters");
        }
        if (description != null && description.length() > 500) {
            throw new IllegalArgumentException("description must be at most 500 characters");
        }
        Objects.requireNonNull(rule, "rule");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }

    public BadgeDefinition withContent(String newName, String newDescription, UUID newImageFileId, BadgeRule newRule, Instant now) {
        return new BadgeDefinition(id, code, newName, newDescription, newImageFileId, newRule, active, createdAt, now);
    }

    public BadgeDefinition withActive(boolean value, Instant now) {
        return new BadgeDefinition(id, code, name, description, imageFileId, rule, value, createdAt, now);
    }
}
