package com.adn.dabaeum.attendance.infrastructure.mybatis;

import java.time.Instant;
import java.util.UUID;

public record AttendanceQrTokenRow(
    UUID id,
    UUID sessionId,
    String tokenHash,
    UUID issuedBy,
    Instant issuedAt,
    Instant expiresAt,
    Instant revokedAt,
    Instant createdAt
) {
}
