package com.adn.dabaeum.attendance.api;

import com.adn.dabaeum.attendance.domain.AttendanceStatus;
import java.time.Instant;
import java.util.UUID;

public record AttendanceAdjustmentResponse(
    UUID id,
    AttendanceStatus fromStatus,
    AttendanceStatus toStatus,
    String reason,
    UUID adjustedBy,
    String adjustedByName,
    Instant adjustedAt
) {
}
