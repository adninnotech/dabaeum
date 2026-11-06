package com.adn.dabaeum.badge.api;

import com.adn.dabaeum.badge.application.BadgeDefinitionCommand;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** 생성·수정 공용 본문. code 는 생성에서만 쓰고 수정에서는 무시한다(바꿀 수 없다). */
@JsonIgnoreProperties(ignoreUnknown = false)
public record BadgeDefinitionRequest(
    @Size(max = 50) String code,
    @NotBlank @Size(max = 100) String name,
    @Size(max = 500) String description,
    UUID imageFileId,
    @NotNull @Valid BadgeRulePayload rule
) {

    BadgeDefinitionCommand toCommand() {
        return new BadgeDefinitionCommand(code, name, description, imageFileId,
            rule.type(), rule.courseId(), rule.classificationCode(), rule.count(), rule.tierLevels());
    }
}
