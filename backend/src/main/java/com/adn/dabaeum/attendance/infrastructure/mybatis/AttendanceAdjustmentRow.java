package com.adn.dabaeum.attendance.infrastructure.mybatis;

import java.time.Instant;
import java.util.UUID;

public record AttendanceAdjustmentRow(
    UUID id,
    UUID attendanceRecordId,
    String beforeStatus,
    String afterStatus,
    String reason,
    UUID adjustedBy,
    Instant adjustedAt,
    Instant createdAt
) {
}
