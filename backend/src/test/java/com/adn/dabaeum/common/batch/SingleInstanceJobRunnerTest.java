package com.adn.dabaeum.common.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

class SingleInstanceJobRunnerTest {

    private final RecordingTransactionManager transactions = new RecordingTransactionManager();

    @Test
    void runsJobWhenLockIsAcquired() {
        AtomicBoolean ran = new AtomicBoolean();
        SingleInstanceJobRunner runner = new SingleInstanceJobRunner(key -> true, transactions);

        assertThat(runner.runExclusively(42L, () -> ran.set(true))).isTrue();
        assertThat(ran).isTrue();
        assertThat(transactions.committed).isTrue();
    }

    @Test
    void skipsJobWhenAnotherInstanceHoldsTheLock() {
        AtomicBoolean ran = new AtomicBoolean();
        SingleInstanceJobRunner runner = new SingleInstanceJobRunner(key -> false, transactions);

        assertThat(runner.runExclusively(42L, () -> ran.set(true))).isFalse();
        assertThat(ran).isFalse();
    }

    @Test
    void failingJobRollsBackAndPropagates() {
        SingleInstanceJobRunner runner = new SingleInstanceJobRunner(key -> true, transactions);

        assertThatThrownBy(() -> runner.runExclusively(42L, () -> {
            throw new IllegalStateException("boom");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(transactions.rolledBack).isTrue();
    }

    private static final class RecordingTransactionManager extends AbstractPlatformTransactionManager {
        boolean committed;
        boolean rolledBack;

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
            committed = true;
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
            rolledBack = true;
        }
    }
}
