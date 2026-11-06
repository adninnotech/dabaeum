package com.adn.dabaeum.common.batch;

import static org.assertj.core.api.Assertions.assertThat;

import com.adn.dabaeum.support.RemotePostgresIntegrationTestSupport;
import java.sql.Connection;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;

class SingleInstanceJobRunnerIntegrationTest extends RemotePostgresIntegrationTestSupport {

    /** 운영 키와 겹치지 않는 테스트 전용 키. */
    private static final long TEST_KEY = 7_349_999L;

    @Autowired
    AdvisoryLock lock;

    @Autowired
    PlatformTransactionManager transactionManager;

    @Test
    void skipsWhileAnotherSessionHoldsTheLockAndRunsAfterItIsReleased() throws Exception {
        SingleInstanceJobRunner runner = new SingleInstanceJobRunner(lock, transactionManager);
        AtomicBoolean ranWhileLocked = new AtomicBoolean();

        // dataSource 는 트랜잭션 비인식 원본이라 테스트 트랜잭션과 다른 세션(= 다른 서버 역할)이다.
        try (Connection otherInstance = dataSource.getConnection()) {
            otherInstance.setAutoCommit(false);
            try (Statement statement = otherInstance.createStatement()) {
                statement.execute("SELECT pg_advisory_xact_lock(" + TEST_KEY + ")");
            }

            assertThat(runner.runExclusively(TEST_KEY, () -> ranWhileLocked.set(true))).isFalse();
            assertThat(ranWhileLocked).isFalse();

            otherInstance.rollback();
        }

        AtomicBoolean ranAfterRelease = new AtomicBoolean();
        assertThat(runner.runExclusively(TEST_KEY, () -> ranAfterRelease.set(true))).isTrue();
        assertThat(ranAfterRelease).isTrue();
    }
}
