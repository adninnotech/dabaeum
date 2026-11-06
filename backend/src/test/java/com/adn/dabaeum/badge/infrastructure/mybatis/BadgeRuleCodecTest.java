package com.adn.dabaeum.badge.infrastructure.mybatis;

import static org.assertj.core.api.Assertions.assertThat;

import com.adn.dabaeum.badge.domain.BadgeRule;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 설계서 §11.4 — 단계 기준표가 JSONB 로 오간다. */
class BadgeRuleCodecTest {

    @Test
    void tieredLevelsRoundTrip() {
        BadgeRule.Tiered rule = new BadgeRule.Tiered(List.of(
            new BadgeRule.TierLevel(1, "씨앗", 1), new BadgeRule.TierLevel(2, "새싹", 6)), "TSIX_040000");

        String json = BadgeRuleCodec.toJson(rule);

        assertThat(json).contains("\"levels\"").contains("\"minCount\":6");
        assertThat(BadgeRuleCodec.fromJson("TIERED", json)).isEqualTo(rule);
    }
}
