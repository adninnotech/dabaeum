package com.adn.dabaeum.recommendation.application;

import com.adn.dabaeum.common.batch.AdvisoryLockKeys;
import com.adn.dabaeum.common.batch.RetentionTask;
import com.adn.dabaeum.recommendation.domain.RecommendationRepository;
import java.time.Instant;
import java.time.ZoneId;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/** 추천 설정 바인딩과 노출 로그 1년 보존 (설계 §7, data-retention-policy.md). */
@Configuration(proxyBeanMethods = false)
@Profile({"local", "dev"})
@EnableConfigurationProperties(RecommendationProperties.class)
public class RecommendationConfiguration {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    static Instant oneYearBefore(Instant now) {
        return now.atZone(SEOUL).minusYears(1).toInstant();
    }

    @Bean
    RetentionTask recommendationImpressionRetentionTask(RecommendationRepository repository) {
        return new RetentionTask() {
            @Override
            public String name() {
                return "recommendation-impressions";
            }

            @Override
            public long lockKey() {
                return AdvisoryLockKeys.RECOMMENDATION_IMPRESSION_CLEANUP;
            }

            @Override
            public int purge(Instant now) {
                return repository.deleteImpressionsBefore(oneYearBefore(now));
            }
        };
    }
}
