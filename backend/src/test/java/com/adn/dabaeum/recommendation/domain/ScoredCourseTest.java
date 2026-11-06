package com.adn.dabaeum.recommendation.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** docs/specs/course-recommendation.md §12.5 — 점수 행은 강좌·0~1 점수·기여 묶음이 있어야 한다. */
class ScoredCourseTest {

    @Test
    void 경계값_0과_1은_받는다() {
        assertThat(new ScoredCourse(UUID.randomUUID(), BigDecimal.ZERO, RecommendationTopFactor.PRICE).score())
            .isEqualByComparingTo("0");
        assertThat(new ScoredCourse(UUID.randomUUID(), BigDecimal.ONE, RecommendationTopFactor.PRICE).score())
            .isEqualByComparingTo("1");
    }

    @Test
    void 빈_값이나_범위_밖_점수는_거부한다() {
        UUID id = UUID.randomUUID();
        assertThatThrownBy(() -> new ScoredCourse(null, BigDecimal.ONE, RecommendationTopFactor.PRICE))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ScoredCourse(id, null, RecommendationTopFactor.PRICE))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ScoredCourse(id, BigDecimal.ONE, null))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ScoredCourse(id, new BigDecimal("-0.00001"), RecommendationTopFactor.PRICE))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ScoredCourse(id, new BigDecimal("1.00001"), RecommendationTopFactor.PRICE))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
