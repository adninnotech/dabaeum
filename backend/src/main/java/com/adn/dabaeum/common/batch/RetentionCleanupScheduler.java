package com.adn.dabaeum.common.batch;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

/** 매일 한 번 보존 기간 삭제를 돌린다. 작업마다 잠금·트랜잭션이 따로라 하나가 실패해도 나머지는 돈다. */
public final class RetentionCleanupScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(RetentionCleanupScheduler.class);

    private final List<RetentionTask> tasks;
    private final SingleInstanceJobRunner runner;
    private final Clock clock;

    public RetentionCleanupScheduler(List<RetentionTask> tasks, SingleInstanceJobRunner runner, Clock clock) {
        this.tasks = List.copyOf(tasks);
        this.runner = Objects.requireNonNull(runner, "runner");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Scheduled(cron = "${dabaeum.batch.retention-cron:0 30 4 * * *}", zone = "Asia/Seoul")
    public void runOnce() {
        Instant now = clock.instant();
        for (RetentionTask task : tasks) {
            runTask(task, now);
        }
    }

    private void runTask(RetentionTask task, Instant now) {
        AtomicInteger deleted = new AtomicInteger();
        try {
            boolean ran = runner.runExclusively(task.lockKey(), () -> deleted.set(task.purge(now)));
            if (ran) {
                LOGGER.info("RETENTION_PURGED task={} deleted={}", task.name(), deleted.get());
            } else {
                LOGGER.info("RETENTION_SKIPPED task={} reason=locked-by-other-instance", task.name());
            }
        } catch (RuntimeException exception) {
            LOGGER.warn("RETENTION_FAILED task={} type={}", task.name(), exception.getClass().getName(), exception);
        }
    }
}
