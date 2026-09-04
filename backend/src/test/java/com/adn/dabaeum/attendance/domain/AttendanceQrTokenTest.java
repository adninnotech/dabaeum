package com.adn.dabaeum.attendance.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AttendanceQrTokenTest {

    @Test
    void isValidOnlyWithinIssuedAndExpiryWindowAndBeforeRevocation() {
        Instant issuedAt = Instant.parse("2026-08-05T00:00:00Z");
        Instant expiresAt = issuedAt.plusSeconds(30);
        AttendanceQrToken token = new AttendanceQrToken(
            UUID.randomUUID(),
            UUID.randomUUID(),
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
            UUID.randomUUID(),
            issuedAt,
            expiresAt,
            null,
            issuedAt
        );

        assertThat(token.isValidAt(issuedAt.minusNanos(1))).isFalse();
        assertThat(token.isValidAt(issuedAt)).isTrue();
        assertThat(token.isValidAt(expiresAt.minusNanos(1))).isTrue();
        assertThat(token.isValidAt(expiresAt)).isFalse();
        AttendanceQrToken revoked = new AttendanceQrToken(
            token.id(),
            token.sessionId(),
            token.tokenHash(),
            token.issuedBy(),
            token.issuedAt(),
            token.expiresAt(),
            issuedAt.plusSeconds(1),
            token.createdAt()
        );
        assertThat(revoked.isValidAt(issuedAt.plusSeconds(2))).isFalse();
    }
}
