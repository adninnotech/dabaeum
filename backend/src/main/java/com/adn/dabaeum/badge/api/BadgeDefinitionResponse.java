package com.adn.dabaeum.badge.api;

import com.adn.dabaeum.badge.domain.BadgeDefinition;
import java.time.Instant;
import java.util.UUID;

public record BadgeDefinitionResponse(
    UUID id,
    String code,
    String name,
    String description,
    UUID imageFileId,
    String imageUrl,
    BadgeRulePayload rule,
    boolean active,
    Instant createdAt,
    Instant updatedAt
) {

    static BadgeDefinitionResponse from(BadgeDefinition definition) {
        return new BadgeDefinitionResponse(
            definition.id(), definition.code(), definition.name(), definition.description(),
            definition.imageFileId(), imageUrl(definition.imageFileId()), BadgeRulePayload.from(definition.rule()),
            definition.active(), definition.createdAt(), definition.updatedAt());
    }

    /** 공개 파일 경로(인증 없이 열린다). 이미지가 없으면 null. */
    static String imageUrl(UUID fileId) {
        return fileId == null ? null : "/api/v1/files/" + fileId + "/content";
    }
}
