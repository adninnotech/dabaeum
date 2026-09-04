package com.adn.dabaeum.attendance.application;

import com.adn.dabaeum.attendance.domain.AttendanceMethod;
import com.adn.dabaeum.attendance.domain.AttendanceSource;
import com.adn.dabaeum.attendance.domain.AttendanceStatus;
import java.time.Instant;
import java.util.UUID;

public record RecordAttendanceCommand(
    UUID sessionId,
    UUID enrollmentId,
    AttendanceMethod attendanceMethod,
    AttendanceStatus status,
    Instant checkedAt,
    AttendanceSource source,
    String qrToken
) {
}
