package com.adn.dabaeum.badge.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** 설계서 §11.4 — 이수기준 문장에는 분류 코드 대신 분류 이름이 나온다. */
class BadgeCriteriaTest {

    private static final Map<String, List<String>> PATHS = Map.of(
        "TSIX_040000", List.of("문화예술교육"),
        "TSIX_040201", List.of("문화예술교육", "생활문화예술", "생활공예"));

    private static String describe(BadgeRule rule) {
        return BadgeCriteria.describe(rule, code -> PATHS.getOrDefault(code, List.of()));
    }

    @Test
    void classificationIsNamedByItsLeafName() {
        assertThat(describe(new BadgeRule.CourseCompletion(null, "TSIX_040201"))).isEqualTo("생활공예 분야 강좌 1건 이수");
        assertThat(describe(new BadgeRule.Cumulative(3, "TSIX_040000"))).isEqualTo("문화예술교육 분야 강좌 누적 3건 이수");
        assertThat(describe(new BadgeRule.Cumulative(2, null))).isEqualTo("강좌 누적 2건 이수");
        assertThat(describe(new BadgeRule.CourseCompletion(UUID.randomUUID(), null))).isEqualTo("지정 강좌 이수");
    }

    @Test
    void unknownClassificationNeverShowsTheCode() {
        assertThat(describe(new BadgeRule.Cumulative(3, "TSIX_990000"))).isEqualTo("지정 분야 강좌 누적 3건 이수");
    }

    @Test
    void tiersListRanges() {
        assertThat(describe(new BadgeRule.Tiered(List.of(
            new BadgeRule.TierLevel(1, "씨앗", 1), new BadgeRule.TierLevel(2, "새싹", 6)), null)))
            .isEqualTo("강좌 이수 건수 — 씨앗 1~5건, 새싹 6건 이상");
    }
}
