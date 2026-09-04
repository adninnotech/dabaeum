package com.adn.dabaeum.attendance.application;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record ValidatedAttendanceQrToken(
    UUID tokenId,
    UUID sessionId,
    Instant issuedAt,
    Instant expiresAt
) {

    public ValidatedAttendanceQrToken {
        Objects.requireNonNull(tokenId, "tokenId");
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(issuedAt, "issuedAt");
        Objects.requireNonNull(expiresAt, "expiresAt");
    }
}
