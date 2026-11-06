package com.adn.dabaeum.popularity.application;

import com.adn.dabaeum.common.batch.AdvisoryLockKeys;
import com.adn.dabaeum.common.batch.SingleInstanceJobRunner;
import com.adn.dabaeum.popularity.domain.PopularityRepository;
import java.time.Clock;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;

/** 인기 점수 재계산 배치 (설계서 §8.2). 매시 한 서버만 돈다(advisory lock). 배치가 꺼져 있으면(통합 테스트) 스케줄러도 없다. */
@Configuration(proxyBeanMethods = false)
@Profile({"local", "dev"})
@EnableConfigurationProperties(PopularityProperties.class)
public class PopularityConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "dabaeum.batch", name = "enabled", havingValue = "true", matchIfMissing = true)
    PopularityScheduler popularityScheduler(
        PopularityRepository repository, PopularityProperties properties, SingleInstanceJobRunner runner, Clock clock
    ) {
        return new PopularityScheduler(repository, properties, runner, clock);
    }

    public static final class PopularityScheduler {

        private static final Logger LOGGER = LoggerFactory.getLogger(PopularityScheduler.class);

        private final PopularityRepository repository;
        private final PopularityProperties properties;
        private final SingleInstanceJobRunner runner;
        private final Clock clock;

        PopularityScheduler(
            PopularityRepository repository, PopularityProperties properties, SingleInstanceJobRunner runner, Clock clock
        ) {
            this.repository = repository;
            this.properties = properties;
            this.runner = runner;
            this.clock = clock;
        }

        @Scheduled(cron = "${dabaeum.popularity.cron:0 5 * * * *}", zone = "Asia/Seoul")
        public void runOnce() {
            AtomicInteger rows = new AtomicInteger();
            try {
                boolean ran = runner.runExclusively(AdvisoryLockKeys.POPULARITY_RECALCULATION, () -> rows.set(
                    repository.recalculate(properties.windowDays(), properties.enrollmentWeight(),
                        properties.viewWeight(), clock.instant())));
                LOGGER.info(ran ? "POPULARITY_RECALCULATED rows={}" : "POPULARITY_SKIPPED rows={} reason=locked", rows.get());
            } catch (RuntimeException exception) {
                LOGGER.warn("POPULARITY_FAILED type={}", exception.getClass().getName(), exception);
            }
        }
    }
}
