package com.adn.dabaeum.usage.application;

import com.adn.dabaeum.common.batch.AdvisoryLockKeys;
import com.adn.dabaeum.common.batch.RetentionTask;
import com.adn.dabaeum.common.batch.SingleInstanceJobRunner;
import com.adn.dabaeum.usage.domain.UsageEventRepository;
import com.adn.dabaeum.usage.domain.UsageSaltRepository;
import com.adn.dabaeum.usage.domain.UsageStatisticsRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * 이용 통계 일별 집계 배치와 보존 (docs/specs/usage-statistics.md §5, §9). 집계는 매시 20분에 돌고 밀린 날이 없으면
 * 조회 한 번으로 끝난다. 원문은 1년, salt 는 그날만 남긴다. 모두 한 서버만 돈다(advisory lock).
 * 배치가 꺼져 있으면(통합 테스트) 스케줄러도 없다.
 */
@Configuration(proxyBeanMethods = false)
@Profile({"local", "dev"})
public class UsageConfiguration {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    @Bean
    UsageAggregator usageAggregator(UsageStatisticsRepository repository) {
        return new UsageAggregator(repository);
    }

    @Bean
    @ConditionalOnProperty(prefix = "dabaeum.batch", name = "enabled", havingValue = "true", matchIfMissing = true)
    UsageAggregationScheduler usageAggregationScheduler(
        UsageAggregator aggregator, SingleInstanceJobRunner runner, Clock clock
    ) {
        return new UsageAggregationScheduler(aggregator, runner, clock);
    }

    @Bean
    RetentionTask usageEventRetentionTask(UsageEventRepository events) {
        return new RetentionTask() {
            @Override
            public String name() {
                return "usage-events";
            }

            @Override
            public long lockKey() {
                return AdvisoryLockKeys.USAGE_EVENT_CLEANUP;
            }

            @Override
            public int purge(Instant now) {
                return events.deleteEventsBefore(now.atZone(SEOUL).toLocalDate().minusYears(1));
            }
        };
    }

    /** 오늘 salt 만 남긴다. 지난 salt 가 없으면 회원 목록으로 키를 다시 계산할 수 없다(§3.1). */
    @Bean
    RetentionTask usageSaltRetentionTask(UsageSaltRepository salts) {
        return new RetentionTask() {
            @Override
            public String name() {
                return "usage-salts";
            }

            @Override
            public long lockKey() {
                return AdvisoryLockKeys.USAGE_SALT_CLEANUP;
            }

            @Override
            public int purge(Instant now) {
                return salts.deleteSaltsBefore(now.atZone(SEOUL).toLocalDate());
            }
        };
    }

    public static final class UsageAggregationScheduler {

        private static final Logger LOGGER = LoggerFactory.getLogger(UsageAggregationScheduler.class);

        private final UsageAggregator aggregator;
        private final SingleInstanceJobRunner runner;
        private final Clock clock;

        UsageAggregationScheduler(UsageAggregator aggregator, SingleInstanceJobRunner runner, Clock clock) {
            this.aggregator = aggregator;
            this.runner = runner;
            this.clock = clock;
        }

        @Scheduled(cron = "${dabaeum.usage.aggregation-cron:0 20 * * * *}", zone = "Asia/Seoul")
        public void runOnce() {
            AtomicInteger days = new AtomicInteger();
            try {
                boolean ran = runner.runExclusively(AdvisoryLockKeys.USAGE_DAILY_AGGREGATION,
                    () -> days.set(aggregator.aggregatePending(clock.instant())));
                LOGGER.info(ran ? "USAGE_AGGREGATED days={}" : "USAGE_AGGREGATION_SKIPPED days={} reason=locked", days.get());
            } catch (RuntimeException exception) {
                LOGGER.warn("USAGE_AGGREGATION_FAILED type={}", exception.getClass().getName(), exception);
            }
        }
    }
}
