package com.adn.dabaeum.attendance.application;

import java.time.Instant;
import java.util.Objects;

public record IssuedAttendanceQrToken(String token, Instant expiresAt) {

    public IssuedAttendanceQrToken {
        Objects.requireNonNull(token, "token");
        Objects.requireNonNull(expiresAt, "expiresAt");
    }
}
