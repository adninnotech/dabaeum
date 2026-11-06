package com.adn.dabaeum.recommendation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

/** docs/specs/course-recommendation.md §12.2 — 모델 배치 설정 기본값과 범위 검증. */
class RecommendationModelPropertiesTest {

    @Test
    void 값을_안_주면_기본값을_쓴다() {
        RecommendationModelProperties properties =
            new RecommendationModelProperties(null, null, null, null, null, null, null, null, null);

        assertThat(properties.enabled()).isFalse();
        assertThat(properties.cron()).isEqualTo("0 0 3 * * *");
        assertThat(properties.timeout()).isEqualTo(Duration.ofMinutes(30));
        assertThat(properties.minPositiveSamples()).isEqualTo(2000);
        assertThat(properties.topNPerUser()).isEqualTo(50);
    }

    @Test
    void 켜져_있으면_경로를_모두_요구한다() {
        assertThatThrownBy(() -> new RecommendationModelProperties(
            true, null, "/venv/bin/python", " ", null, null, null, "/reports", "/models"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 꺼져_있으면_경로가_없어도_된다() {
        RecommendationModelProperties properties =
            new RecommendationModelProperties(false, null, null, null, null, null, null, null, null);

        assertThat(properties.python()).isNull();
    }

    @Test
    void 켜져_있고_경로가_모두_있으면_통과한다() {
        RecommendationModelProperties properties = new RecommendationModelProperties(
            true, "0 0 4 * * *", "/venv/bin/python", "/ml/run.py", Duration.ofMinutes(5), 10, 5, "/reports", "/models");

        assertThat(properties.enabled()).isTrue();
        assertThat(properties.topNPerUser()).isEqualTo(5);
    }

    @Test
    void 타임아웃은_양수여야_한다() {
        assertThatThrownBy(() -> new RecommendationModelProperties(
            null, null, null, null, Duration.ZERO, null, null, null, null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 표본_기준과_회원별_건수는_1_이상이어야_한다() {
        assertThatThrownBy(() -> new RecommendationModelProperties(
            null, null, null, null, null, 0, null, null, null))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RecommendationModelProperties(
            null, null, null, null, null, null, 0, null, null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void cron_은_비울_수_없다() {
        assertThatThrownBy(() -> new RecommendationModelProperties(
            null, " ", null, null, null, null, null, null, null))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
