package com.adn.dabaeum.attendance.api;

import com.adn.dabaeum.attendance.application.AttendanceQrTokenApplicationService;
import com.adn.dabaeum.attendance.application.AttendanceApplicationService;
import com.adn.dabaeum.attendance.application.AttendancePage;
import com.adn.dabaeum.attendance.application.AttendanceMetrics;
import com.adn.dabaeum.attendance.application.IssuedAttendanceQrToken;
import com.adn.dabaeum.attendance.domain.Attendance;
import com.adn.dabaeum.common.api.ApiResponse;
import com.adn.dabaeum.common.security.CurrentUserProvider;
import com.adn.dabaeum.common.web.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class AttendanceController {

    private final AttendanceQrTokenApplicationService qrService;
    private final AttendanceApplicationService attendanceService;
    private final AttendanceApiMapper mapper;
    private final CurrentUserProvider currentUserProvider;

    public AttendanceController(
        AttendanceQrTokenApplicationService qrService,
        AttendanceApplicationService attendanceService,
        AttendanceApiMapper mapper,
        CurrentUserProvider currentUserProvider
    ) {
        this.qrService = qrService;
        this.attendanceService = attendanceService;
        this.mapper = mapper;
        this.currentUserProvider = currentUserProvider;
    }

    @PostMapping("/sessions/{sessionId}/qr-token")
    public ResponseEntity<ApiResponse<AttendanceQrTokenResponse>> issueQrToken(
        @PathVariable UUID sessionId,
        HttpServletRequest request
    ) {
        IssuedAttendanceQrToken issued = qrService.issue(
            sessionId, currentUserProvider.requireContext());
        String requestId = (String) request.getAttribute(RequestIdFilter.ATTRIBUTE_NAME);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(mapper.toQrResponse(issued, requestId));
    }

    @GetMapping("/sessions/{sessionId}/attendance")
    public AttendancePageResponse listAttendance(
        @PathVariable UUID sessionId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(defaultValue = "createdAt,desc") String sort,
        HttpServletRequest request
    ) {
        AttendancePage result = attendanceService.list(
            mapper.toListQuery(sessionId, page, size, sort),
            currentUserProvider.requireContext());
        return mapper.toPageResponse(result, requestId(request));
    }

    @PostMapping("/sessions/{sessionId}/attendance")
    public ResponseEntity<ApiResponse<AttendanceResponse>> recordAttendance(
        @PathVariable UUID sessionId,
        @Valid @RequestBody AttendanceCreateRequest requestBody,
        HttpServletRequest request
    ) {
        Attendance created = attendanceService.record(
            mapper.toRecordCommand(sessionId, requestBody),
            currentUserProvider.requireContext());
        return ResponseEntity.created(URI.create("/api/v1/attendance/" + created.id()))
            .body(mapper.toAttendanceResponse(created, requestId(request)));
    }

    @GetMapping("/attendance/{attendanceId}")
    public ApiResponse<AttendanceResponse> getAttendance(
        @PathVariable UUID attendanceId,
        HttpServletRequest request
    ) {
        Attendance attendance = attendanceService.get(
            attendanceId, currentUserProvider.requireContext());
        return mapper.toAttendanceResponse(attendance, requestId(request));
    }

    @PatchMapping("/attendance/{attendanceId}")
    public ApiResponse<AttendanceResponse> adjustAttendance(
        @PathVariable UUID attendanceId,
        @Valid @RequestBody AttendanceAdjustmentRequest requestBody,
        HttpServletRequest request
    ) {
        Attendance adjusted = attendanceService.adjust(
            mapper.toAdjustmentCommand(attendanceId, requestBody),
            currentUserProvider.requireContext());
        return mapper.toAttendanceResponse(adjusted, requestId(request));
    }

    @GetMapping("/enrollments/{enrollmentId}/attendance-summary")
    public ApiResponse<AttendanceSummaryResponse> getAttendanceSummary(
        @PathVariable UUID enrollmentId,
        HttpServletRequest request
    ) {
        AttendanceMetrics metrics = attendanceService.summary(
            enrollmentId, currentUserProvider.requireContext());
        return mapper.toSummaryResponse(metrics, requestId(request));
    }

    private String requestId(HttpServletRequest request) {
        return (String) request.getAttribute(RequestIdFilter.ATTRIBUTE_NAME);
    }
}
