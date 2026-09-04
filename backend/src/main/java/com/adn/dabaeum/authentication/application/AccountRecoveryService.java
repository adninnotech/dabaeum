package com.adn.dabaeum.authentication.application;

import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import java.time.Instant;
import java.util.UUID;

public interface AccountRecoveryService {

    boolean isEmailAvailable(String email);

    void requestPasswordReset(String email);

    void confirmPasswordReset(String token, String password);

    AdminResetResult adminResetPassword(
        AuthenticatedUserContext actor, UUID userId, String temporaryPassword);

    record AdminResetResult(Instant resetAt, String temporaryPassword) {
    }
}
