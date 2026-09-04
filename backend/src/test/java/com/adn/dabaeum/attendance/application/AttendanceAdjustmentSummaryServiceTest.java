package com.adn.dabaeum.attendance.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.adn.dabaeum.attendance.domain.Attendance;
import com.adn.dabaeum.attendance.domain.AttendanceAdjustment;
import com.adn.dabaeum.attendance.domain.AttendanceAdjustmentRepository;
import com.adn.dabaeum.attendance.domain.AttendanceMethod;
import com.adn.dabaeum.attendance.domain.AttendanceRepository;
import com.adn.dabaeum.attendance.domain.AttendanceSource;
import com.adn.dabaeum.attendance.domain.AttendanceStatus;
import com.adn.dabaeum.authorization.application.AuthorizationPolicy;
import com.adn.dabaeum.common.api.ApiErrorCode;
import com.adn.dabaeum.common.api.ApiException;
import com.adn.dabaeum.common.security.AuthenticatedRole;
import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import com.adn.dabaeum.course.domain.Course;
import com.adn.dabaeum.course.domain.CourseEducationType;
import com.adn.dabaeum.course.domain.CourseInstructorRepository;
import com.adn.dabaeum.course.domain.CourseRepository;
import com.adn.dabaeum.course.domain.CourseSession;
import com.adn.dabaeum.course.domain.CourseSessionRepository;
import com.adn.dabaeum.course.domain.CourseSessionStatus;
import com.adn.dabaeum.course.domain.CourseStatus;
import com.adn.dabaeum.enrollment.domain.Enrollment;
import com.adn.dabaeum.enrollment.domain.EnrollmentApplicationType;
import com.adn.dabaeum.enrollment.domain.EnrollmentRepository;
import com.adn.dabaeum.enrollment.domain.EnrollmentStatus;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AttendanceAdjustmentSummaryServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-05T00:00:00Z");
    private static final UUID ATTENDANCE_ID = UUID.fromString(
        "11111111-1111-1111-1111-111111111111");
    private static final UUID SESSION_ID = UUID.fromString(
        "22222222-2222-2222-2222-222222222222");
    private static final UUID COURSE_ID = UUID.fromString(
        "33333333-3333-3333-3333-333333333333");
    private static final UUID INSTITUTION_ID = UUID.fromString(
        "44444444-4444-4444-4444-444444444444");
    private static final UUID USER_ID = UUID.fromString(
        "55555555-5555-5555-5555-555555555555");
    private static final UUID MANAGER_ID = UUID.fromString(
        "66666666-6666-6666-6666-666666666666");
    private static final UUID ENROLLMENT_ID = UUID.fromString(
        "77777777-7777-7777-7777-777777777777");

    @Mock AttendanceRepository attendanceRepository;
    @Mock AttendanceAdjustmentRepository adjustmentRepository;
    @Mock CourseSessionRepository sessionRepository;
    @Mock CourseRepository courseRepository;
    @Mock CourseInstructorRepository instructorRepository;
    @Mock EnrollmentRepository enrollmentRepository;
    @Mock AttendanceQrTokenApplicationService qrTokenService;

    private AttendanceApplicationService service;

    @BeforeEach
    void setUp() {
        service = new DefaultAttendanceApplicationService(
            attendanceRepository, sessionRepository, courseRepository, instructorRepository,
            enrollmentRepository, qrTokenService, new AuthorizationPolicy(),
            Clock.fixed(NOW, ZoneOffset.UTC), () -> UUID.fromString(
                "88888888-8888-8888-8888-888888888888"), adjustmentRepository,
            new DefaultAttendanceMetricsQuery(sessionRepository, attendanceRepository,
                enrollmentRepository));
    }

    @Test
    void adjustsWithRowLockAppendOnlyHistoryAndConditionalStatusUpdate() {
        when(attendanceRepository.findByIdForUpdate(ATTENDANCE_ID))
            .thenReturn(Optional.of(attendance(AttendanceStatus.PRESENT)));
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session()));
        when(courseRepository.findActiveById(COURSE_ID)).thenReturn(Optional.of(course()));
        when(instructorRepository.existsByCourseIdAndUserIdAndRole(any(), any(), any()))
            .thenReturn(true);
        Attendance updated = attendance(AttendanceStatus.LATE);
        when(attendanceRepository.updateStatus(
            ATTENDANCE_ID, AttendanceStatus.PRESENT, AttendanceStatus.LATE, NOW))
            .thenReturn(true);
        when(attendanceRepository.findById(ATTENDANCE_ID)).thenReturn(Optional.of(updated));

        Attendance result = service.adjust(new AdjustAttendanceCommand(
            ATTENDANCE_ID, AttendanceStatus.LATE, "현장 확인"), manager());

        assertThat(result.status()).isEqualTo(AttendanceStatus.LATE);
        verify(adjustmentRepository).save(any(AttendanceAdjustment.class));
        verify(attendanceRepository).updateStatus(
            ATTENDANCE_ID, AttendanceStatus.PRESENT, AttendanceStatus.LATE, NOW);
    }

    @Test
    void rejectsSameStatusAndInvalidReasonBeforeWriting() {
        when(attendanceRepository.findByIdForUpdate(ATTENDANCE_ID))
            .thenReturn(Optional.of(attendance(AttendanceStatus.PRESENT)));
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session()));
        when(courseRepository.findActiveById(COURSE_ID)).thenReturn(Optional.of(course()));
        when(instructorRepository.existsByCourseIdAndUserIdAndRole(any(), any(), any()))
            .thenReturn(true);
        assertThatThrownBy(() -> service.adjust(new AdjustAttendanceCommand(
            ATTENDANCE_ID, AttendanceStatus.PRESENT, "same"), manager()))
            .isInstanceOf(ApiException.class)
            .extracting("code")
            .isEqualTo(ApiErrorCode.VALIDATION_FAILED);
        assertThatThrownBy(() -> service.adjust(new AdjustAttendanceCommand(
            ATTENDANCE_ID, AttendanceStatus.LATE, " "), manager()))
            .isInstanceOf(ApiException.class)
            .extracting("code")
            .isEqualTo(ApiErrorCode.VALIDATION_FAILED);
    }

    @Test
    void calculatesClosedSessionsIncludingMissingAsAbsentAndRoundsRate() {
        Enrollment enrollment = enrollment();
        when(enrollmentRepository.findById(ENROLLMENT_ID)).thenReturn(Optional.of(enrollment));
        when(sessionRepository.findAttendanceEligibleByCourseId(COURSE_ID, NOW))
            .thenReturn(List.of(
                session("11111111-1111-1111-1111-111111111111", CourseSessionStatus.COMPLETED,
                    NOW.minusSeconds(7200), NOW.minusSeconds(3600)),
                session("22222222-2222-2222-2222-222222222222", CourseSessionStatus.OPEN,
                    NOW.minusSeconds(1800), NOW.plusSeconds(1800)),
                session("33333333-3333-3333-3333-333333333333", CourseSessionStatus.COMPLETED,
                    NOW.minusSeconds(3600), NOW.minusSeconds(1800))));
        when(attendanceRepository.findByEnrollment(ENROLLMENT_ID)).thenReturn(List.of(
            attendanceForSession(UUID.fromString("11111111-1111-1111-1111-111111111111"),
                AttendanceStatus.PRESENT),
            attendanceForSession(UUID.fromString("22222222-2222-2222-2222-222222222222"),
                AttendanceStatus.LATE)));

        AttendanceMetrics metrics = new DefaultAttendanceMetricsQuery(
            sessionRepository, attendanceRepository, enrollmentRepository)
            .calculate(ENROLLMENT_ID, NOW);

        assertThat(metrics.totalSessions()).isEqualTo(3);
        assertThat(metrics.presentCount()).isEqualTo(1);
        assertThat(metrics.lateCount()).isEqualTo(1);
        assertThat(metrics.absentCount()).isEqualTo(1);
        assertThat(metrics.attendanceRate()).isEqualByComparingTo("66.67");
        assertThat(metrics.completedMinutes()).isEqualTo(120);
    }

    private Attendance attendance(AttendanceStatus status) {
        return new Attendance(ATTENDANCE_ID, COURSE_ID, SESSION_ID, ENROLLMENT_ID, null,
            AttendanceMethod.ADMIN, status, NOW, AttendanceSource.ADMIN_WEB, MANAGER_ID,
            NOW, NOW);
    }

    private Attendance attendanceForSession(UUID sessionId, AttendanceStatus status) {
        return new Attendance(UUID.randomUUID(), COURSE_ID, sessionId, ENROLLMENT_ID, null,
            AttendanceMethod.ADMIN, status, NOW, AttendanceSource.ADMIN_WEB, MANAGER_ID,
            NOW, NOW);
    }

    private CourseSession session() {
        return session("22222222-2222-2222-2222-222222222222", CourseSessionStatus.OPEN,
            NOW.minusSeconds(60), NOW.plusSeconds(600));
    }

    private CourseSession session(String id, CourseSessionStatus status,
        Instant startsAt, Instant endsAt) {
        UUID sessionId = UUID.fromString(id);
        return new CourseSession(sessionId, COURSE_ID, 1, startsAt, endsAt, null,
            startsAt, endsAt, status, NOW, NOW);
    }

    private Course course() {
        return new Course(COURSE_ID, INSTITUTION_ID, "C-001", "Course", null, null,
            CourseEducationType.OFFLINE, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31),
            null, null, 30, null, null, false, null, CourseStatus.IN_PROGRESS, NOW, NOW, null);
    }

    private Enrollment enrollment() {
        // 승인 시각을 모든 회차보다 앞에 둔다. 승인 이후 회차만 분모에 들어가므로,
        // 승인 전에 시작한 회차를 결석으로 세지 않는 규칙에 걸리지 않게 한다.
        return new Enrollment(ENROLLMENT_ID, COURSE_ID, USER_ID, null,
            EnrollmentApplicationType.SELF, EnrollmentStatus.APPROVED, NOW.minusSeconds(90_000),
            NOW.minusSeconds(86_400), null, null, null, null, null, NOW.minusSeconds(90_000), NOW);
    }

    private AuthenticatedUserContext manager() {
        return new AuthenticatedUserContext(MANAGER_ID, "LOCAL",
            Set.of(new AuthenticatedRole("INSTRUCTOR", INSTITUTION_ID)));
    }
}
