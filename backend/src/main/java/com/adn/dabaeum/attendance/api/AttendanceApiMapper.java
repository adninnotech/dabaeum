package com.adn.dabaeum.attendance.api;

import com.adn.dabaeum.attendance.config.DadaeguAttendProperties;
import com.adn.dabaeum.attendance.application.IssuedAttendanceQrToken;
import com.adn.dabaeum.attendance.application.AttendanceDetail;
import com.adn.dabaeum.attendance.application.AttendancePage;
import com.adn.dabaeum.attendance.application.AttendanceMetrics;
import com.adn.dabaeum.attendance.application.AdjustAttendanceCommand;
import com.adn.dabaeum.attendance.application.ListSessionAttendanceQuery;
import com.adn.dabaeum.attendance.application.RecordAttendanceCommand;
import com.adn.dabaeum.attendance.domain.Attendance;
import com.adn.dabaeum.attendance.domain.AttendanceLearner;
import com.adn.dabaeum.common.api.ApiMeta;
import com.adn.dabaeum.common.api.ApiResponse;
import com.adn.dabaeum.common.api.PageMeta;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Objects;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

// QR 발급 응답의 attendUrl 을 만들려면 설정이 필요하다. 여기서 끌어오면
// @Import(AttendanceApiMapper.class) 만 하는 @WebMvcTest 들도 그대로 뜬다.
@Component
@EnableConfigurationProperties(DadaeguAttendProperties.class)
public class AttendanceApiMapper {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private final Clock clock;
    private final DadaeguAttendProperties attendProperties;

    public AttendanceApiMapper(Clock clock, DadaeguAttendProperties attendProperties) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.attendProperties = Objects.requireNonNull(attendProperties, "attendProperties");
    }

    public ApiResponse<AttendanceQrTokenResponse> toQrResponse(
        IssuedAttendanceQrToken issued,
        String requestId
    ) {
        return new ApiResponse<>(
            new AttendanceQrTokenResponse(issued.token(), issued.expiresAt(),
                attendProperties.attendUrl(issued.token())),
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
        List<AttendanceResponse> data = page.data().stream()
            .map(attendance -> toListResponse(attendance,
                page.learners().get(attendance.enrollmentId())))
            .toList();
        return new AttendancePageResponse(
            data,
            new PageMeta(page.page(), page.size(), page.totalElements(), page.totalPages()),
            apiMeta(requestId)
        );
    }

    private AttendanceResponse toListResponse(Attendance attendance, AttendanceLearner learner) {
        AttendanceResponse base = toResponse(attendance);
        if (learner == null) {
            return base;
        }
        return new AttendanceResponse(
            base.id(), base.courseId(), base.sessionId(), base.enrollmentId(),
            base.attendanceMethod(), base.status(), base.checkedAt(), base.source(),
            base.createdBy(), base.createdAt(), base.updatedAt(),
            learner.userId(), learner.name(), null);
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
            attendance.updatedAt(),
            null,
            null,
            null
        );
    }

    public ApiResponse<AttendanceResponse> toDetailResponse(
        AttendanceDetail detail,
        String requestId
    ) {
        AttendanceResponse base = toResponse(detail.attendance());
        List<AttendanceAdjustmentResponse> adjustments = detail.adjustments().stream()
            .map(entry -> new AttendanceAdjustmentResponse(
                entry.id(), entry.fromStatus(), entry.toStatus(), entry.reason(),
                entry.adjustedBy(), entry.adjustedByName(), entry.adjustedAt()))
            .toList();
        return new ApiResponse<>(new AttendanceResponse(
            base.id(), base.courseId(), base.sessionId(), base.enrollmentId(),
            base.attendanceMethod(), base.status(), base.checkedAt(), base.source(),
            base.createdBy(), base.createdAt(), base.updatedAt(), null, null, adjustments),
            apiMeta(requestId));
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
