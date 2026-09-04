package com.adn.dabaeum.attendance.application;

import java.math.BigDecimal;
import java.util.UUID;

public record AttendanceMetrics(
    UUID enrollmentId,
    int totalSessions,
    int presentCount,
    int lateCount,
    int absentCount,
    int excusedCount,
    BigDecimal attendanceRate,
    int completedMinutes
) {
    public AttendanceMetrics {
        attendanceRate = attendanceRate.setScale(2);
    }
}
