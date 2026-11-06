package com.adn.dabaeum.accesslog.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.adn.dabaeum.accesslog.domain.AdminAccessLogRepository;
import com.adn.dabaeum.common.batch.AdvisoryLockKeys;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** 설계서 §14 — 관리자 접속기록 2년 보존. */
@ExtendWith(MockitoExtension.class)
class AdminAccessLogRetentionTaskTest {

    private static final Instant NOW = Instant.parse("2026-09-28T00:00:00Z");

    @Mock AdminAccessLogRepository repository;

    @Test
    void deletesLogsOlderThanTwoYears() {
        when(repository.deleteCreatedBefore(Instant.parse("2024-09-28T00:00:00Z"))).thenReturn(2);
        AdminAccessLogRetentionTask task = new AdminAccessLogRetentionTask(repository);

        assertThat(task.purge(NOW)).isEqualTo(2);
        verify(repository).deleteCreatedBefore(Instant.parse("2024-09-28T00:00:00Z"));
        assertThat(task.lockKey()).isEqualTo(AdvisoryLockKeys.ADMIN_ACCESS_LOG_CLEANUP);
        assertThat(task.name()).isEqualTo("admin-access-logs");
    }
}
