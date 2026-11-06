package com.adn.dabaeum.badge.api;

import com.adn.dabaeum.badge.domain.BadgeRule;
import com.adn.dabaeum.badge.domain.BadgeRuleType;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

/**
 * 규칙 요청·응답 공용. type 에 맞는 필드만 쓴다: COURSE_COMPLETION = courseId 또는 classificationCode,
 * CUMULATIVE = count(+classificationCode), TIERED = levels(+classificationCode).
 */
@JsonIgnoreProperties(ignoreUnknown = false)
public record BadgeRulePayload(
    @NotNull BadgeRuleType type,
    UUID courseId,
    String classificationCode,
    Integer count,
    List<TierLevelPayload> levels
) {

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record TierLevelPayload(Integer level, String name, Integer minCount, UUID imageFileId, String imageUrl) {

        BadgeRule.TierLevel toTier() {
            return new BadgeRule.TierLevel(level == null ? 0 : level, name, minCount == null ? 0 : minCount,
                imageFileId);
        }

        /** imageUrl 은 응답 전용이다(요청에 오면 무시). */
        static TierLevelPayload from(BadgeRule.TierLevel tier) {
            return new TierLevelPayload(tier.level(), tier.name(), tier.minCount(), tier.imageFileId(),
                BadgeDefinitionResponse.imageUrl(tier.imageFileId()));
        }
    }

    List<BadgeRule.TierLevel> tierLevels() {
        return levels == null ? null : levels.stream().map(level -> level == null ? null : level.toTier()).toList();
    }


    static BadgeRulePayload from(BadgeRule rule) {
        return switch (rule) {
            case BadgeRule.CourseCompletion completion ->
                new BadgeRulePayload(rule.type(), completion.courseId(), completion.classificationCode(), null, null);
            case BadgeRule.Cumulative cumulative ->
                new BadgeRulePayload(rule.type(), null, cumulative.classificationCode(), cumulative.count(), null);
            case BadgeRule.Tiered tiered -> new BadgeRulePayload(rule.type(), null, tiered.classificationCode(), null,
                tiered.levels().stream().map(TierLevelPayload::from).toList());
        };
    }
}
