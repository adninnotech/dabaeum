package com.adn.dabaeum.attendance.infrastructure.mybatis;

import java.time.Instant;
import java.util.UUID;

public record AttendanceRow(
    UUID id,
    UUID courseId,
    UUID sessionId,
    UUID enrollmentId,
    UUID qrTokenId,
    String attendanceMethod,
    String status,
    Instant checkedAt,
    String source,
    UUID createdBy,
    Instant createdAt,
    Instant updatedAt
) {
}
