package com.adn.dabaeum.attendance.infrastructure.mybatis;

import java.time.Instant;
import java.util.UUID;

public record AttendanceAdjustmentEntryRow(
    UUID id,
    String beforeStatus,
    String afterStatus,
    String reason,
    UUID adjustedBy,
    String adjustedByName,
    Instant adjustedAt
) {
}
