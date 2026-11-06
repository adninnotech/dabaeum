package com.adn.dabaeum.attendance.domain;

import java.util.List;
import java.util.UUID;

public interface AttendanceAdjustmentRepository {

    void save(AttendanceAdjustment adjustment);

    List<AttendanceAdjustment> findByAttendanceId(UUID attendanceId);

    /** 출결 상세용. 최신순이며 보정자 이름을 함께 읽는다. */
    List<AttendanceAdjustmentEntry> findEntriesByAttendanceId(UUID attendanceId);
}
