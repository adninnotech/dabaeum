package com.adn.dabaeum.attendance.api;

import java.math.BigDecimal;
import java.util.UUID;

public record AttendanceSummaryResponse(
    UUID enrollmentId,
    int totalSessions,
    int presentCount,
    int lateCount,
    int absentCount,
    int excusedCount,
    BigDecimal attendanceRate,
    int completedMinutes
) {
}
