package com.adn.dabaeum.attendance.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record Attendance(
    UUID id,
    UUID courseId,
    UUID sessionId,
    UUID enrollmentId,
    UUID qrTokenId,
    AttendanceMethod attendanceMethod,
    AttendanceStatus status,
    Instant checkedAt,
    AttendanceSource source,
    UUID createdBy,
    Instant createdAt,
    Instant updatedAt
) {

    public Attendance {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(courseId, "courseId");
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(enrollmentId, "enrollmentId");
        Objects.requireNonNull(attendanceMethod, "attendanceMethod");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (createdAt.isAfter(updatedAt)) {
            throw new IllegalArgumentException("createdAt must not be after updatedAt");
        }

        switch (attendanceMethod) {
            case QR -> {
                if (qrTokenId == null || source != AttendanceSource.APP) {
                    throw new IllegalArgumentException(
                        "QR attendance requires APP source and qrTokenId");
                }
            }
            case ADMIN -> {
                if (qrTokenId != null || source != AttendanceSource.ADMIN_WEB) {
                    throw new IllegalArgumentException(
                        "ADMIN attendance requires ADMIN_WEB source and no qrTokenId");
                }
            }
            case EXTERNAL -> {
                if (qrTokenId != null || source != AttendanceSource.EXTERNAL_API) {
                    throw new IllegalArgumentException(
                        "EXTERNAL attendance requires EXTERNAL_API source and no qrTokenId");
                }
            }
        }
    }
}
