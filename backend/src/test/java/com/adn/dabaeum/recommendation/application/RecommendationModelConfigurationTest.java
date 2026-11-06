package com.adn.dabaeum.recommendation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.adn.dabaeum.common.batch.AdvisoryLock;
import com.adn.dabaeum.common.batch.SingleInstanceJobRunner;
import com.adn.dabaeum.popularity.application.PopularityProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.transaction.PlatformTransactionManager;

/** 모델 배치 빈은 model.enabled=true 이고 batch 가 꺼져 있지 않을 때만 생긴다 (§12.2, §12.7). */
class RecommendationModelConfigurationTest {

    private final ApplicationContextRunner contexts = new ApplicationContextRunner()
        .withUserConfiguration(RecommendationModelConfiguration.class)
        .withBean(PopularityProperties.class, () -> new PopularityProperties(30, 3, 1))
        .withBean(SingleInstanceJobRunner.class,
            () -> new SingleInstanceJobRunner(mock(AdvisoryLock.class), mock(PlatformTransactionManager.class)))
        .withPropertyValues(
            "spring.profiles.active=dev",
            "spring.datasource.url=jdbc:postgresql://127.0.0.1:15432/dabaeum_dev",
            "spring.datasource.username=u",
            "spring.datasource.password=p",
            "dabaeum.recommendation.model.python=/venv/bin/python",
            "dabaeum.recommendation.model.script=/ml/run.py",
            "dabaeum.recommendation.model.report-dir=/ml/reports",
            "dabaeum.recommendation.model.model-dir=/ml/models");

    @Test
    void 기본값은_꺼져_있다() {
        contexts.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(RecommendationModelScheduler.class);
            assertThat(context).doesNotHaveBean(ModelProcessRunner.class);
        });
    }

    @Test
    void 켜면_스케줄러와_실행기가_생긴다() {
        contexts.withPropertyValues("dabaeum.recommendation.model.enabled=true").run(context -> {
            assertThat(context).hasSingleBean(RecommendationModelScheduler.class);
            assertThat(context).hasSingleBean(ModelProcessRunner.class);
            assertThat(context.getBean(RecommendationModelProperties.class).timeout()).hasMinutes(30);
        });
    }

    @Test
    void 배치가_꺼져_있으면_켜도_생기지_않는다() {
        contexts.withPropertyValues("dabaeum.recommendation.model.enabled=true", "dabaeum.batch.enabled=false")
            .run(context -> assertThat(context).doesNotHaveBean(RecommendationModelScheduler.class));
    }
}
