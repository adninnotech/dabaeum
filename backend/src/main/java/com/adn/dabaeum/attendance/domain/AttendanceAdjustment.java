package com.adn.dabaeum.attendance.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record AttendanceAdjustment(
    UUID id,
    UUID attendanceRecordId,
    AttendanceStatus beforeStatus,
    AttendanceStatus afterStatus,
    String reason,
    UUID adjustedBy,
    Instant adjustedAt,
    Instant createdAt
) {

    public AttendanceAdjustment {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(attendanceRecordId, "attendanceRecordId");
        Objects.requireNonNull(beforeStatus, "beforeStatus");
        Objects.requireNonNull(afterStatus, "afterStatus");
        Objects.requireNonNull(adjustedBy, "adjustedBy");
        Objects.requireNonNull(adjustedAt, "adjustedAt");
        Objects.requireNonNull(createdAt, "createdAt");
        if (beforeStatus == afterStatus) {
            throw new IllegalArgumentException("beforeStatus and afterStatus must differ");
        }
        if (reason == null || reason.isBlank() || reason.length() > 1000) {
            throw new IllegalArgumentException("reason must be 1 to 1000 characters");
        }
        reason = reason.trim();
        if (createdAt.isAfter(adjustedAt)) {
            throw new IllegalArgumentException("createdAt must not be after adjustedAt");
        }
    }
}
