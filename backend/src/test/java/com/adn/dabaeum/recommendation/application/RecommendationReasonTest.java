package com.adn.dabaeum.recommendation.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.adn.dabaeum.recommendation.domain.RecommendationReasonType;
import com.adn.dabaeum.recommendation.domain.RecommendationTopFactor;
import com.adn.dabaeum.recommendation.domain.ScoredCourse;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** docs/specs/course-recommendation.md §12.5 — 모델 점수 이유는 score·topFactor 만 채우고 나머지는 null. */
class RecommendationReasonTest {

    @Test
    void 모델_점수_이유는_점수와_기여_묶음만_채운다() {
        ScoredCourse scored = new ScoredCourse(UUID.randomUUID(), new BigDecimal("0.45678"),
            RecommendationTopFactor.SCHEDULE);

        RecommendationReason reason = RecommendationReason.modelScore(scored);

        assertThat(reason.type()).isEqualTo(RecommendationReasonType.MODEL_SCORE);
        assertThat(reason.score()).isEqualByComparingTo("0.45678");
        assertThat(reason.topFactor()).isEqualTo(RecommendationTopFactor.SCHEDULE);
        assertThat(reason.classificationCode()).isNull();
        assertThat(reason.sourceCourseId()).isNull();
        assertThat(reason.ageBand()).isNull();
    }

    @Test
    void 규칙_기반_이유는_점수가_없다() {
        assertThat(RecommendationReason.popular().score()).isNull();
        assertThat(RecommendationReason.popular().topFactor()).isNull();
        assertThat(RecommendationReason.popularAgeBand("30").score()).isNull();
    }
}
