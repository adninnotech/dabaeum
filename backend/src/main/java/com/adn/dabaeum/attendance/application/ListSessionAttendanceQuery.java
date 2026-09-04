package com.adn.dabaeum.attendance.application;

import java.util.UUID;

public record ListSessionAttendanceQuery(
    UUID sessionId,
    int page,
    int size,
    String sort
) {
}
