package com.adn.dabaeum.attendance.domain;

import java.time.Instant;
import java.util.Base64;
import java.util.Objects;
import java.util.UUID;

public record AttendanceQrTokenPayload(
    int version,
    UUID tokenId,
    UUID sessionId,
    Instant issuedAt,
    Instant expiresAt,
    String nonce
) {

    public AttendanceQrTokenPayload {
        if (version != 1) {
            throw new IllegalArgumentException("Unsupported QR token version");
        }
        Objects.requireNonNull(tokenId, "tokenId");
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(issuedAt, "issuedAt");
        Objects.requireNonNull(expiresAt, "expiresAt");
        Objects.requireNonNull(nonce, "nonce");
        if (!issuedAt.isBefore(expiresAt)) {
            throw new IllegalArgumentException("issuedAt must be before expiresAt");
        }
        try {
            if (Base64.getUrlDecoder().decode(nonce).length != 32) {
                throw new IllegalArgumentException("nonce must contain 32 bytes");
            }
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("nonce must be URL-safe Base64", exception);
        }
    }
}
