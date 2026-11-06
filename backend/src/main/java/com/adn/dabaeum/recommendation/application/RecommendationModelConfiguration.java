package com.adn.dabaeum.recommendation.application;

import com.adn.dabaeum.common.batch.SingleInstanceJobRunner;
import com.adn.dabaeum.popularity.application.PopularityProperties;
import com.adn.dabaeum.recommendation.application.RecommendationModelScheduler.DatabaseCredentials;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * 추천 모델 학습 배치 (docs/specs/course-recommendation.md §12.2, §12.7). model.enabled=true 이고 배치가 꺼져 있지 않을 때만
 * 빈을 만든다 — 기본은 꺼져 있어 Python 이 없는 환경에서도 기동된다.
 */
@Configuration(proxyBeanMethods = false)
@Profile({"local", "dev"})
@EnableConfigurationProperties(RecommendationModelProperties.class)
public class RecommendationModelConfiguration {

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(prefix = "dabaeum.batch", name = "enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnProperty(prefix = "dabaeum.recommendation.model", name = "enabled", havingValue = "true")
    static class Enabled {

        @Bean
        ModelProcessRunner modelProcessRunner() {
            return new DefaultModelProcessRunner();
        }

        @Bean
        RecommendationModelScheduler recommendationModelScheduler(
            RecommendationModelProperties properties,
            PopularityProperties popularity,
            ModelProcessRunner processes,
            SingleInstanceJobRunner runner,
            @Value("${spring.datasource.url:}") String url,
            @Value("${spring.datasource.username:}") String username,
            @Value("${spring.datasource.password:}") String password
        ) {
            return new RecommendationModelScheduler(
                properties, popularity, new DatabaseCredentials(url, username, password), processes,
                runner::runExclusively);
        }
    }
}
