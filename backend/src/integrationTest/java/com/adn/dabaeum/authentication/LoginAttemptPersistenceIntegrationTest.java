package com.adn.dabaeum.authentication;

import static org.assertj.core.api.Assertions.assertThat;

import com.adn.dabaeum.authentication.domain.LoginAttemptRepository;
import com.adn.dabaeum.support.RemotePostgresIntegrationTestSupport;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class LoginAttemptPersistenceIntegrationTest extends RemotePostgresIntegrationTestSupport {

    private static final Instant LOCK_UNTIL = Instant.parse("2026-09-28T00:30:00Z");

    @Autowired LoginAttemptRepository repository;

    @Test
    void fifthFailureLocksAndResetsTheCounter() {
        UUID userId = insertUser();

        for (int attempt = 1; attempt <= 4; attempt++) {
            assertThat(repository.recordFailure(userId, 5, LOCK_UNTIL)).isFalse();
        }
        assertThat(repository.recordFailure(userId, 5, LOCK_UNTIL)).isTrue();

        assertThat(repository.findLockedUntil(userId)).contains(LOCK_UNTIL);
        assertThat(failedCount(userId)).isZero();
    }

    @Test
    void clearRemovesLockAndCount() {
        UUID userId = insertUser();
        repository.recordFailure(userId, 5, LOCK_UNTIL);
        jdbcTemplate.update("UPDATE tb_users SET locked_until = now() + interval '10 minutes' WHERE id = ?", userId);

        repository.clear(userId);

        assertThat(repository.findLockedUntil(userId)).isEmpty();
        assertThat(failedCount(userId)).isZero();
    }

    @Test
    void unknownUserIsNeitherCountedNorLocked() {
        assertThat(repository.recordFailure(UUID.randomUUID(), 5, LOCK_UNTIL)).isFalse();
    }

    private UUID insertUser() {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO tb_users (id, name) VALUES (?, ?)", id, "잠금");
        return id;
    }

    private int failedCount(UUID userId) {
        return jdbcTemplate.queryForObject(
            "SELECT failed_login_count FROM tb_users WHERE id = ?", Integer.class, userId);
    }
}
