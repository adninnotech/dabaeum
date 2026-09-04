package com.adn.dabaeum.authentication.domain;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface PasswordResetTokenRepository {

    void save(PasswordResetToken token);

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    boolean markUsed(UUID id, Instant usedAt);
}
