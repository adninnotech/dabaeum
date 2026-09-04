package com.adn.dabaeum.attendance.api;

import com.adn.dabaeum.attendance.domain.AttendanceMethod;
import com.adn.dabaeum.attendance.domain.AttendanceSource;
import com.adn.dabaeum.attendance.domain.AttendanceStatus;
import java.time.Instant;
import java.util.UUID;

public record AttendanceResponse(
    UUID id,
    UUID courseId,
    UUID sessionId,
    UUID enrollmentId,
    AttendanceMethod attendanceMethod,
    AttendanceStatus status,
    Instant checkedAt,
    AttendanceSource source,
    UUID createdBy,
    Instant createdAt,
    Instant updatedAt
) {
}
