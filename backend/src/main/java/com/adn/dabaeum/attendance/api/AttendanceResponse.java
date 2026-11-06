package com.adn.dabaeum.attendance.api;

import com.adn.dabaeum.attendance.domain.AttendanceMethod;
import com.adn.dabaeum.attendance.domain.AttendanceSource;
import com.adn.dabaeum.attendance.domain.AttendanceStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * userId·userName 은 회차 출결 목록에서만 싣는다. 단건 응답에는 키가 없다.
 * adjustments 는 단건 조회(GET /attendance/{id})에서만 싣는다(최신순).
 */
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
    Instant updatedAt,
    @JsonInclude(JsonInclude.Include.NON_NULL) UUID userId,
    @JsonInclude(JsonInclude.Include.NON_NULL) String userName,
    @JsonInclude(JsonInclude.Include.NON_NULL) List<AttendanceAdjustmentResponse> adjustments
) {
}
