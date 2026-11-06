package com.adn.dabaeum.badge.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/** 설계서 §11.1 — 규칙은 두 종류뿐이고 파라미터 조합이 정해져 있다. */
class BadgeRuleTest {

    @Test
    void courseCompletionNeedsExactlyOneTarget() {
        assertThat(new BadgeRule.CourseCompletion(UUID.randomUUID(), null).type())
            .isEqualTo(BadgeRuleType.COURSE_COMPLETION);
        assertThat(new BadgeRule.CourseCompletion(null, "TSIX_040000").classificationCode()).isEqualTo("TSIX_040000");
        assertThatThrownBy(() -> new BadgeRule.CourseCompletion(null, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BadgeRule.CourseCompletion(UUID.randomUUID(), "TSIX_040000"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void cumulativeNeedsPositiveCount() {
        assertThat(new BadgeRule.Cumulative(3, "TSIX_040000").type()).isEqualTo(BadgeRuleType.CUMULATIVE);
        assertThat(new BadgeRule.Cumulative(1, null).classificationCode()).isNull();
        assertThatThrownBy(() -> new BadgeRule.Cumulative(0, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BadgeRule.Cumulative(1001, null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void classificationCodeMustLookLikeAStandardCode() {
        assertThatThrownBy(() -> new BadgeRule.Cumulative(2, "문화예술")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BadgeRule.CourseCompletion(null, "TSIX_04")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void tieredPicksHighestReachedLevel() {
        BadgeRule.Tiered rule = seedTiers();

        assertThat(rule.type()).isEqualTo(BadgeRuleType.TIERED);
        assertThat(rule.levelFor(0)).isEmpty();
        assertThat(rule.levelFor(1)).hasValueSatisfying(level -> assertThat(level.name()).isEqualTo("씨앗"));
        assertThat(rule.levelFor(5)).hasValueSatisfying(level -> assertThat(level.level()).isEqualTo(1));
        assertThat(rule.levelFor(6)).hasValueSatisfying(level -> assertThat(level.name()).isEqualTo("새싹"));
        assertThat(rule.levelFor(30)).hasValueSatisfying(level -> assertThat(level.name()).isEqualTo("펼침"));
        assertThat(rule.levelFor(500)).hasValueSatisfying(level -> assertThat(level.level()).isEqualTo(7));
        assertThat(rule.level(3)).hasValueSatisfying(level -> assertThat(level.name()).isEqualTo("자람"));
    }

    @Test
    void tieredLevelsMustBeConsecutiveAndIncreasing() {
        var one = new BadgeRule.TierLevel(1, "씨앗", 1);
        assertThatThrownBy(() -> new BadgeRule.Tiered(java.util.List.of(one), null))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BadgeRule.Tiered(java.util.List.of(one, new BadgeRule.TierLevel(3, "자람", 6)), null))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BadgeRule.Tiered(java.util.List.of(one, new BadgeRule.TierLevel(2, "새싹", 1)), null))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BadgeRule.Tiered(java.util.List.of(one, new BadgeRule.TierLevel(2, " ", 6)), null))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BadgeRule.Tiered(java.util.List.of(one, new BadgeRule.TierLevel(2, "씨앗", 6)), null))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BadgeRule.Tiered(null, null)).isInstanceOf(IllegalArgumentException.class);
    }

    static BadgeRule.Tiered seedTiers() {
        return new BadgeRule.Tiered(java.util.List.of(
            new BadgeRule.TierLevel(1, "씨앗", 1), new BadgeRule.TierLevel(2, "새싹", 6),
            new BadgeRule.TierLevel(3, "자람", 11), new BadgeRule.TierLevel(4, "이음", 16),
            new BadgeRule.TierLevel(5, "펼침", 21), new BadgeRule.TierLevel(6, "빛남", 31),
            new BadgeRule.TierLevel(7, "나눔", 41)), null);
    }

    @Test
    void definitionCodeIsUpperSnake() {
        assertThatThrownBy(() -> definition("culture-3")).isInstanceOf(IllegalArgumentException.class);
        assertThat(definition("CULTURE_3").code()).isEqualTo("CULTURE_3");
    }

    private static BadgeDefinition definition(String code) {
        java.time.Instant now = java.time.Instant.parse("2026-09-28T00:00:00Z");
        return new BadgeDefinition(UUID.randomUUID(), code, "문화예술 3과목", null, null,
            new BadgeRule.Cumulative(3, "TSIX_040000"), true, now, now);
    }
}
