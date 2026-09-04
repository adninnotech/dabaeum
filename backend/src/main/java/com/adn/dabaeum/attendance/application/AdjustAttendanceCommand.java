package com.adn.dabaeum.attendance.application;

import com.adn.dabaeum.attendance.domain.AttendanceStatus;
import java.util.UUID;

public record AdjustAttendanceCommand(
    UUID attendanceId,
    AttendanceStatus status,
    String reason
) {
}
