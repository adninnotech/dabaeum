package com.adn.dabaeum.attendance.application;

import com.adn.dabaeum.attendance.domain.Attendance;
import com.adn.dabaeum.attendance.domain.AttendanceAdjustmentEntry;
import java.util.List;
import java.util.Objects;

/** 출결 단건 조회 결과. 보정 이력은 최신순이다. */
public record AttendanceDetail(
    Attendance attendance,
    List<AttendanceAdjustmentEntry> adjustments
) {

    public AttendanceDetail {
        Objects.requireNonNull(attendance, "attendance");
        adjustments = List.copyOf(Objects.requireNonNull(adjustments, "adjustments"));
    }
}
