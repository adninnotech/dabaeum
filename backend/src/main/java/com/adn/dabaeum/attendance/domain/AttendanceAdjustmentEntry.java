package com.adn.dabaeum.attendance.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** 출결 상세에 싣는 보정 이력 한 건. 보정자 이름은 탈퇴 등으로 없을 수 있다. */
public record AttendanceAdjustmentEntry(
    UUID id,
    AttendanceStatus fromStatus,
    AttendanceStatus toStatus,
    String reason,
    UUID adjustedBy,
    String adjustedByName,
    Instant adjustedAt
) {

    public AttendanceAdjustmentEntry {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(fromStatus, "fromStatus");
        Objects.requireNonNull(toStatus, "toStatus");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(adjustedBy, "adjustedBy");
        Objects.requireNonNull(adjustedAt, "adjustedAt");
    }
}
