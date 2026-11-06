package com.adn.dabaeum.common.batch;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class RetentionCleanupSchedulerTest {

    private static final Instant NOW = Instant.parse("2026-09-28T19:30:00Z");

    @Test
    void oneFailingTaskDoesNotStopTheOthers() {
        List<String> purged = new ArrayList<>();
        RetentionTask failing = task("failing", 1L, now -> {
            throw new IllegalStateException("boom");
        });
        RetentionTask working = task("working", 2L, now -> {
            purged.add("working@" + now);
            return 3;
        });
        SingleInstanceJobRunner runner = new SingleInstanceJobRunner(key -> true, new NoopTransactionManager());

        new RetentionCleanupScheduler(List.of(failing, working), runner, Clock.fixed(NOW, ZoneOffset.UTC)).runOnce();

        assertThat(purged).containsExactly("working@" + NOW);
    }

    @Test
    void lockedTaskIsSkipped() {
        List<String> purged = new ArrayList<>();
        RetentionTask task = task("locked", 7L, now -> {
            purged.add("ran");
            return 1;
        });
        SingleInstanceJobRunner runner = new SingleInstanceJobRunner(key -> key != 7L, new NoopTransactionManager());

        new RetentionCleanupScheduler(List.of(task), runner, Clock.fixed(NOW, ZoneOffset.UTC)).runOnce();

        assertThat(purged).isEmpty();
    }

    private static RetentionTask task(String name, long key, java.util.function.ToIntFunction<Instant> purge) {
        return new RetentionTask() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public long lockKey() {
                return key;
            }

            @Override
            public int purge(Instant now) {
                return purge.applyAsInt(now);
            }
        };
    }

    private static final class NoopTransactionManager
        extends org.springframework.transaction.support.AbstractPlatformTransactionManager {
        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, org.springframework.transaction.TransactionDefinition definition) {
        }

        @Override
        protected void doCommit(org.springframework.transaction.support.DefaultTransactionStatus status) {
        }

        @Override
        protected void doRollback(org.springframework.transaction.support.DefaultTransactionStatus status) {
        }
    }
}
