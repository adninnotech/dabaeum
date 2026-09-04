package com.adn.dabaeum.attendance.domain;

import java.util.List;
import java.util.UUID;

public interface AttendanceAdjustmentRepository {

    void save(AttendanceAdjustment adjustment);

    List<AttendanceAdjustment> findByAttendanceId(UUID attendanceId);
}
