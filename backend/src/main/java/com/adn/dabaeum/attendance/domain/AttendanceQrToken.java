package com.adn.dabaeum.attendance.domain;

import java.time.Instant;
import java.util.UUID;

public record AttendanceQrToken(
    UUID id,
    UUID sessionId,
    String tokenHash,
    UUID issuedBy,
    Instant issuedAt,
    Instant expiresAt,
    Instant revokedAt,
    Instant createdAt
) {

    public boolean isValidAt(Instant now) {
        return revokedAt == null
            && !now.isBefore(issuedAt)
            && now.isBefore(expiresAt);
    }
}
