package com.adn.dabaeum.attendance.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.adn.dabaeum.attendance.domain.Attendance;
import com.adn.dabaeum.attendance.domain.AttendanceMethod;
import com.adn.dabaeum.attendance.domain.AttendanceRepository;
import com.adn.dabaeum.attendance.domain.AttendanceSource;
import com.adn.dabaeum.attendance.domain.AttendanceStatus;
import com.adn.dabaeum.course.domain.CourseSession;
import com.adn.dabaeum.course.domain.CourseSessionRepository;
import com.adn.dabaeum.course.domain.CourseSessionStatus;
import com.adn.dabaeum.enrollment.domain.Enrollment;
import com.adn.dabaeum.enrollment.domain.EnrollmentApplicationType;
import com.adn.dabaeum.enrollment.domain.EnrollmentRepository;
import com.adn.dabaeum.enrollment.domain.EnrollmentStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * 출석률 분모는 학습자가 출석할 수 있었던 회차로 한정한다. 수강 승인 전에 시작한 회차는
 * 출결 기록이 없으면 대상 밖이고, 기록이 있으면 기록대로 센다.
 */
class DefaultAttendanceMetricsQueryTest {

    private static final Instant APPROVED_AT = Instant.parse("2026-09-01T00:00:00Z");
    private static final UUID ENROLLMENT_ID = UUID.randomUUID();
    private static final UUID COURSE_ID = UUID.randomUUID();

    private final CourseSessionRepository sessions = mock(CourseSessionRepository.class);
    private final AttendanceRepository attendances = mock(AttendanceRepository.class);
    private final EnrollmentRepository enrollments = mock(EnrollmentRepository.class);
    private final DefaultAttendanceMetricsQuery query =
        new DefaultAttendanceMetricsQuery(sessions, attendances, enrollments);

    @Test
    void excludesSessionsThatEndedBeforeApprovalUnlessTheLearnerHasARecord() {
        CourseSession beforeApprovalNoRecord = session(APPROVED_AT.minusSeconds(7_200), 120);
        CourseSession beforeApprovalAttended = session(APPROVED_AT.minusSeconds(3_600), 60);
        CourseSession afterApprovalAttended = session(APPROVED_AT.plusSeconds(3_600), 90);
        CourseSession afterApprovalAbsent = session(APPROVED_AT.plusSeconds(7_200), 30);
        when(enrollments.findById(ENROLLMENT_ID)).thenReturn(Optional.of(enrollment(APPROVED_AT)));
        when(sessions.findAttendanceEligibleByCourseId(COURSE_ID, APPROVED_AT.plusSeconds(86_400)))
            .thenReturn(List.of(beforeApprovalNoRecord, beforeApprovalAttended,
                afterApprovalAttended, afterApprovalAbsent));
        when(attendances.findByEnrollment(ENROLLMENT_ID)).thenReturn(List.of(
            attendance(beforeApprovalAttended, AttendanceStatus.PRESENT),
            attendance(afterApprovalAttended, AttendanceStatus.LATE)));

        AttendanceMetrics metrics = query.calculate(ENROLLMENT_ID, APPROVED_AT.plusSeconds(86_400));

        // 승인 전·기록 없음 1개는 분모에서 빠진다 → 3개 중 출석 인정 2개
        assertThat(metrics.totalSessions()).isEqualTo(3);
        assertThat(metrics.presentCount()).isEqualTo(1);
        assertThat(metrics.lateCount()).isEqualTo(1);
        assertThat(metrics.absentCount()).isEqualTo(1);
        assertThat(metrics.attendanceRate()).isEqualByComparingTo(new BigDecimal("66.67"));
        assertThat(metrics.completedMinutes()).isEqualTo(150);
    }

    @Test
    void countsEverySessionWhenTheEnrollmentHasNoApprovalTime() {
        CourseSession early = session(APPROVED_AT.minusSeconds(7_200), 120);
        when(enrollments.findById(ENROLLMENT_ID)).thenReturn(Optional.of(enrollment(null)));
        when(sessions.findAttendanceEligibleByCourseId(COURSE_ID, APPROVED_AT))
            .thenReturn(List.of(early));
        when(attendances.findByEnrollment(ENROLLMENT_ID)).thenReturn(List.of());

        AttendanceMetrics metrics = query.calculate(ENROLLMENT_ID, APPROVED_AT);

        assertThat(metrics.totalSessions()).isEqualTo(1);
        assertThat(metrics.absentCount()).isEqualTo(1);
        assertThat(metrics.attendanceRate()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    private static CourseSession session(Instant startsAt, int minutes) {
        return new CourseSession(UUID.randomUUID(), COURSE_ID, 1, startsAt,
            startsAt.plusSeconds(minutes * 60L), null, null, null,
            CourseSessionStatus.COMPLETED, startsAt, startsAt);
    }

    private static Attendance attendance(CourseSession session, AttendanceStatus status) {
        return new Attendance(UUID.randomUUID(), COURSE_ID, session.id(), ENROLLMENT_ID, null,
            AttendanceMethod.ADMIN, status, session.startsAt(), AttendanceSource.ADMIN_WEB,
            null, session.startsAt(), session.startsAt());
    }

    private static Enrollment enrollment(Instant approvedAt) {
        Instant appliedAt = APPROVED_AT.minusSeconds(86_400);
        EnrollmentStatus status = approvedAt == null
            ? EnrollmentStatus.APPLIED : EnrollmentStatus.APPROVED;
        return new Enrollment(ENROLLMENT_ID, COURSE_ID, UUID.randomUUID(), null,
            EnrollmentApplicationType.SELF, status, appliedAt, approvedAt, null, null, null,
            null, null, appliedAt, appliedAt);
    }
}
