package com.adn.dabaeum.attendance.application;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record ValidatedAttendanceQrToken(
    UUID tokenId,
    UUID sessionId,
    UUID issuedBy,
    Instant issuedAt,
    Instant expiresAt
) {

    public ValidatedAttendanceQrToken {
        Objects.requireNonNull(tokenId, "tokenId");
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(issuedBy, "issuedBy");
        Objects.requireNonNull(issuedAt, "issuedAt");
        Objects.requireNonNull(expiresAt, "expiresAt");
    }
}
