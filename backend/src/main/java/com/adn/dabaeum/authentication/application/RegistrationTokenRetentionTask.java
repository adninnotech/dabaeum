package com.adn.dabaeum.authentication.application;

import com.adn.dabaeum.authentication.domain.DadaeguRegistrationTokenRepository;
import com.adn.dabaeum.common.batch.AdvisoryLockKeys;
import com.adn.dabaeum.common.batch.RetentionTask;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** 만료 후 1일 지난 다대구 가입 토큰을 지운다 (설계서 §4.2, §14). */
@Component
@Profile({"local", "dev"})
public class RegistrationTokenRetentionTask implements RetentionTask {

    static final Duration GRACE = Duration.ofDays(1);

    private final DadaeguRegistrationTokenRepository repository;

    public RegistrationTokenRetentionTask(DadaeguRegistrationTokenRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    @Override
    public String name() {
        return "registration-tokens";
    }

    @Override
    public long lockKey() {
        return AdvisoryLockKeys.REGISTRATION_TOKEN_CLEANUP;
    }

    @Override
    public int purge(Instant now) {
        return repository.deleteExpiredBefore(now.minus(GRACE));
    }
}
