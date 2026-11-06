package com.adn.dabaeum.authentication.infrastructure.mybatis;

import com.adn.dabaeum.authentication.domain.LoginAttemptRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile({"local", "dev"})
public class LoginAttemptMyBatisRepository implements LoginAttemptRepository {

    private final LoginAttemptMapper mapper;

    public LoginAttemptMyBatisRepository(LoginAttemptMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<Instant> findLockedUntil(UUID userId) {
        return Optional.ofNullable(mapper.selectLockedUntil(userId));
    }

    @Override
    public boolean recordFailure(UUID userId, int threshold, Instant lockUntil) {
        return Boolean.TRUE.equals(mapper.recordFailure(userId, threshold, lockUntil));
    }

    @Override
    public void clear(UUID userId) {
        mapper.clear(userId);
    }
}
