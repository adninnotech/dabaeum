package com.adn.dabaeum.attendance.api;

import java.time.Instant;

public record AttendanceQrTokenResponse(String token, Instant expiresAt) {
}
