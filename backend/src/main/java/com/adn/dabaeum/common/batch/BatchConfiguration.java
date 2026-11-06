package com.adn.dabaeum.common.batch;

import java.time.Clock;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * 주기 배치. MyBatis 저장소가 있는 local/dev 에서만, dabaeum.batch.enabled 가 false 가 아니면 켠다.
 * 스케줄러 스레드는 스프링이 고른다 — 다대구 출석용 TaskScheduler 빈이 하나 있으면 그것을 같이 쓴다(가상 스레드).
 */
@Configuration(proxyBeanMethods = false)
@Profile({"local", "dev"})
@ConditionalOnProperty(prefix = "dabaeum.batch", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableScheduling
public class BatchConfiguration {

    @Bean
    SingleInstanceJobRunner singleInstanceJobRunner(AdvisoryLock lock, PlatformTransactionManager transactionManager) {
        return new SingleInstanceJobRunner(lock, transactionManager);
    }

    @Bean
    RetentionCleanupScheduler retentionCleanupScheduler(
        List<RetentionTask> tasks,
        SingleInstanceJobRunner runner,
        Clock clock
    ) {
        return new RetentionCleanupScheduler(tasks, runner, clock);
    }
}
