package com.adn.dabaeum.attendance.application;

import com.adn.dabaeum.attendance.domain.Attendance;
import com.adn.dabaeum.common.security.AuthenticatedUserContext;

import java.util.UUID;

public interface AttendanceApplicationService {

    Attendance record(
        RecordAttendanceCommand command,
        AuthenticatedUserContext context
    );

    /**
     * 다대구 DID 출결(D안). 학생 폰이 강사 QR(URL)을 찍어 다대구 인증을 마치면 서버가 대신 부른다.
     * 로그인 컨텍스트가 없어 권한은 QR 토큰이 대신하며, createdBy 는 그 토큰의 발급자(강사)다.
     * 토큰은 학생이 찍는 순간 검증된 결과를 받아 오며 여기서 다시 검증하지 않는다.
     */
    Attendance recordDadaegu(RecordDadaeguAttendanceCommand command);

    AttendancePage list(
        ListSessionAttendanceQuery query,
        AuthenticatedUserContext context
    );

    /** 출결 단건과 보정 이력(최신순). */
    AttendanceDetail get(UUID attendanceId, AuthenticatedUserContext context);

    Attendance adjust(
        AdjustAttendanceCommand command,
        AuthenticatedUserContext context
    );

    AttendanceMetrics summary(
        UUID enrollmentId,
        AuthenticatedUserContext context
    );
}
