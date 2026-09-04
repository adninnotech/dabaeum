package com.adn.dabaeum.attendance.api;

import com.adn.dabaeum.attendance.application.IssuedAttendanceQrToken;
import com.adn.dabaeum.attendance.application.AttendancePage;
import com.adn.dabaeum.attendance.application.AttendanceMetrics;
import com.adn.dabaeum.attendance.application.AdjustAttendanceCommand;
import com.adn.dabaeum.attendance.application.ListSessionAttendanceQuery;
import com.adn.dabaeum.attendance.application.RecordAttendanceCommand;
import com.adn.dabaeum.attendance.domain.Attendance;
import com.adn.dabaeum.common.api.ApiMeta;
import com.adn.dabaeum.common.api.ApiResponse;
import com.adn.dabaeum.common.api.PageMeta;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class AttendanceApiMapper {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private final Clock clock;

    public AttendanceApiMapper(Clock clock) {
        this.clock = clock;
    }

    public ApiResponse<AttendanceQrTokenResponse> toQrResponse(
        IssuedAttendanceQrToken issued,
        String requestId
    ) {
        return new ApiResponse<>(
            new AttendanceQrTokenResponse(issued.token(), issued.expiresAt()),
            new ApiMeta(requestId, OffsetDateTime.ofInstant(clock.instant(), SEOUL))
        );
    }

    public RecordAttendanceCommand toRecordCommand(
        UUID sessionId,
        AttendanceCreateRequest request
    ) {
        return new RecordAttendanceCommand(
            sessionId,
            request.enrollmentId(),
            request.attendanceMethod(),
            request.status(),
            request.checkedAt(),
            request.source(),
            request.qrToken()
        );
    }

    public ListSessionAttendanceQuery toListQuery(
        UUID sessionId,
        int page,
        int size,
        String sort
    ) {
        return new ListSessionAttendanceQuery(sessionId, page, size, sort);
    }

    public ApiResponse<AttendanceResponse> toAttendanceResponse(
        Attendance attendance,
        String requestId
    ) {
        return new ApiResponse<>(toResponse(attendance), apiMeta(requestId));
    }

    public AttendancePageResponse toPageResponse(
        AttendancePage page,
        String requestId
    ) {
        List<AttendanceResponse> data = page.data().stream().map(this::toResponse).toList();
        return new AttendancePageResponse(
            data,
            new PageMeta(page.page(), page.size(), page.totalElements(), page.totalPages()),
            apiMeta(requestId)
        );
    }

    public AttendanceResponse toResponse(Attendance attendance) {
        return new AttendanceResponse(
            attendance.id(),
            attendance.courseId(),
            attendance.sessionId(),
            attendance.enrollmentId(),
            attendance.attendanceMethod(),
            attendance.status(),
            attendance.checkedAt(),
            attendance.source(),
            attendance.createdBy(),
            attendance.createdAt(),
            attendance.updatedAt()
        );
    }

    public AdjustAttendanceCommand toAdjustmentCommand(
        UUID attendanceId,
        AttendanceAdjustmentRequest request
    ) {
        return new AdjustAttendanceCommand(attendanceId, request.status(), request.reason());
    }

    public ApiResponse<AttendanceSummaryResponse> toSummaryResponse(
        AttendanceMetrics metrics,
        String requestId
    ) {
        return new ApiResponse<>(
            new AttendanceSummaryResponse(
                metrics.enrollmentId(), metrics.totalSessions(), metrics.presentCount(),
                metrics.lateCount(), metrics.absentCount(), metrics.excusedCount(),
                metrics.attendanceRate(), metrics.completedMinutes()),
            apiMeta(requestId));
    }

    private ApiMeta apiMeta(String requestId) {
        return new ApiMeta(requestId, OffsetDateTime.ofInstant(clock.instant(), SEOUL));
    }
}
