package com.adn.dabaeum.attendance.application;

import com.adn.dabaeum.attendance.domain.Attendance;
import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import java.util.UUID;

public interface AttendanceApplicationService {

    Attendance record(
        RecordAttendanceCommand command,
        AuthenticatedUserContext context
    );

    AttendancePage list(
        ListSessionAttendanceQuery query,
        AuthenticatedUserContext context
    );

    Attendance get(UUID attendanceId, AuthenticatedUserContext context);

    Attendance adjust(
        AdjustAttendanceCommand command,
        AuthenticatedUserContext context
    );

    AttendanceMetrics summary(
        UUID enrollmentId,
        AuthenticatedUserContext context
    );
}
