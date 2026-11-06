package com.adn.dabaeum.badge.application;

import com.adn.dabaeum.badge.domain.BadgeRule;
import com.adn.dabaeum.badge.domain.BadgeRuleType;
import java.util.List;
import java.util.UUID;

/** 생성·수정 공용. code 는 생성에서만 쓴다. 규칙 파라미터는 종류에 맞는 것만 채운다(TIERED 는 levels). */
public record BadgeDefinitionCommand(
    String code,
    String name,
    String description,
    UUID imageFileId,
    BadgeRuleType ruleType,
    UUID courseId,
    String classificationCode,
    Integer count,
    List<BadgeRule.TierLevel> levels
) {

    public BadgeDefinitionCommand(String code, String name, String description, UUID imageFileId,
        BadgeRuleType ruleType, UUID courseId, String classificationCode, Integer count) {
        this(code, name, description, imageFileId, ruleType, courseId, classificationCode, count, null);
    }
}
