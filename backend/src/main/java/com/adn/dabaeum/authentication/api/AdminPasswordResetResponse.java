package com.adn.dabaeum.authentication.api;

import java.time.Instant;

public record AdminPasswordResetResponse(
    Instant resetAt,
    String temporaryPassword
) {
}
