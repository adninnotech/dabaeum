package com.adn.dabaeum.attendance.domain;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface AttendanceQrTokenRepository {

    void revokeActiveBySession(UUID sessionId, Instant revokedAt);

    void save(AttendanceQrToken token);

    Optional<AttendanceQrToken> findByHash(String tokenHash);
}
