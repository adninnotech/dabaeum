package com.adn.dabaeum.authentication.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.adn.dabaeum.authentication.domain.DadaeguRegistrationTokenRepository;
import com.adn.dabaeum.common.batch.AdvisoryLockKeys;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RegistrationTokenRetentionTaskTest {

    private static final Instant NOW = Instant.parse("2026-09-28T19:30:00Z");

    @Mock DadaeguRegistrationTokenRepository repository;

    @Test
    void deletesTokensExpiredMoreThanOneDayAgo() {
        when(repository.deleteExpiredBefore(NOW.minusSeconds(86_400))).thenReturn(4);
        RegistrationTokenRetentionTask task = new RegistrationTokenRetentionTask(repository);

        assertThat(task.purge(NOW)).isEqualTo(4);
        verify(repository).deleteExpiredBefore(Instant.parse("2026-09-27T19:30:00Z"));
        assertThat(task.lockKey()).isEqualTo(AdvisoryLockKeys.REGISTRATION_TOKEN_CLEANUP);
        assertThat(task.name()).isEqualTo("registration-tokens");
    }
}
