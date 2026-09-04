package com.adn.dabaeum.attendance.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.adn.dabaeum.attendance.domain.Attendance;
import com.adn.dabaeum.attendance.domain.AttendanceMethod;
import com.adn.dabaeum.attendance.domain.AttendancePageCriteria;
import com.adn.dabaeum.attendance.domain.AttendanceRepository;
import com.adn.dabaeum.attendance.domain.AttendanceSource;
import com.adn.dabaeum.attendance.domain.AttendanceStatus;
import com.adn.dabaeum.attendance.domain.AttendanceSort;
import com.adn.dabaeum.attendance.domain.AttendanceSortDirection;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AttendanceRecordQueryServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-05T00:00:00Z");
    private static final UUID SESSION_ID = UUID.fromString(
        "22222222-2222-2222-2222-222222222222");
    private static final UUID COURSE_ID = UUID.fromString(
        "33333333-3333-3333-3333-333333333333");
    private static final UUID INSTITUTION_ID = UUID.fromString(
        "44444444-4444-4444-4444-444444444444");
    private static final UUID SUBJECT_ID = UUID.fromString(
        "55555555-5555-5555-5555-555555555555");
    private static final UUID MANAGER_ID = UUID.fromString(
        "66666666-6666-6666-6666-666666666666");
    private static final UUID ENROLLMENT_ID = UUID.fromString(
        "77777777-7777-7777-7777-777777777777");

    @Mock AttendanceRepository attendanceRepository;
    @Mock CourseSessionRepository sessionRepository;
    @Mock CourseRepository courseRepository;
    @Mock CourseInstructorRepository instructorRepository;
    @Mock EnrollmentRepository enrollmentRepository;
    @Mock AttendanceQrTokenApplicationService qrTokenService;

    private AttendanceApplicationService service;

    @BeforeEach
    void setUp() {
        service = new DefaultAttendanceApplicationService(
            attendanceRepository,
            sessionRepository,
            courseRepository,
            instructorRepository,
            enrollmentRepository,
            qrTokenService,
            new AuthorizationPolicy(),
            Clock.fixed(NOW, ZoneOffset.UTC),
            new FixedAttendanceIdGenerator()
        );
    }

    @Test
    void recordsQrForApprovedSubjectWithServerCheckedAt() {
        givenSessionCourseEnrollment();
        when(qrTokenService.validate("a".repeat(32), SESSION_ID))
            .thenReturn(new ValidatedAttendanceQrToken(
                UUID.fromString("88888888-8888-8888-8888-888888888888"),
                SESSION_ID, NOW.minusSeconds(1), NOW.plusSeconds(29)));

        Attendance result = service.record(new RecordAttendanceCommand(
            SESSION_ID, ENROLLMENT_ID, AttendanceMethod.QR, AttendanceStatus.PRESENT,
            null, AttendanceSource.APP, "a".repeat(32)), subject());

        assertThat(result.attendanceMethod()).isEqualTo(AttendanceMethod.QR);
        assertThat(result.checkedAt()).isEqualTo(NOW);
        assertThat(result.createdBy()).isEqualTo(SUBJECT_ID);
        assertThat(result.qrTokenId()).isNotNull();
        verify(attendanceRepository).save(result);
    }

    @Test
    void rejectsQrForDifferentSubjectAndDoesNotPersist() {
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session()));
        when(courseRepository.findActiveById(COURSE_ID)).thenReturn(Optional.of(course()));
        when(enrollmentRepository.findById(ENROLLMENT_ID)).thenReturn(Optional.of(enrollment()));

        assertThatThrownBy(() -> service.record(new RecordAttendanceCommand(
            SESSION_ID, ENROLLMENT_ID, AttendanceMethod.QR, AttendanceStatus.PRESENT,
            null, AttendanceSource.APP, "a".repeat(32)), manager()))
            .isInstanceOf(ApiException.class)
            .extracting("code")
            .isEqualTo(ApiErrorCode.FORBIDDEN);
        verify(attendanceRepository, never()).save(any());
    }

    @Test
    void rejectsExternalAndDuplicateAttendance() {
        givenSessionCourseEnrollment();
        ApiException unsupported = (ApiException) org.assertj.core.api.Assertions.catchThrowable(
            () -> service.record(new RecordAttendanceCommand(
                SESSION_ID, ENROLLMENT_ID, AttendanceMethod.EXTERNAL, AttendanceStatus.PRESENT,
                null, AttendanceSource.EXTERNAL_API, null), subject()));
        assertThat(unsupported.code()).isEqualTo(ApiErrorCode.ATTENDANCE_METHOD_NOT_SUPPORTED);

        when(attendanceRepository.findBySessionAndEnrollment(SESSION_ID, ENROLLMENT_ID))
            .thenReturn(Optional.of(attendance()));
        when(qrTokenService.validate("a".repeat(32), SESSION_ID))
            .thenReturn(new ValidatedAttendanceQrToken(
                UUID.fromString("88888888-8888-8888-8888-888888888888"),
                SESSION_ID, NOW.minusSeconds(1), NOW.plusSeconds(29)));
        assertThatThrownBy(() -> service.record(new RecordAttendanceCommand(
            SESSION_ID, ENROLLMENT_ID, AttendanceMethod.QR, AttendanceStatus.PRESENT,
            null, AttendanceSource.APP, "a".repeat(32)), subject()))
            .isInstanceOf(ApiException.class)
            .extracting("code")
            .isEqualTo(ApiErrorCode.ATTENDANCE_CONFLICT);
    }

    @Test
    void listsAttendanceWithScopedInstructorAndAllowlistedSort() {
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session()));
        when(courseRepository.findActiveById(COURSE_ID)).thenReturn(Optional.of(course()));
        when(instructorRepository.existsByCourseIdAndUserId(COURSE_ID, MANAGER_ID))
            .thenReturn(true);
        when(attendanceRepository.findBySession(eq(SESSION_ID), any()))
            .thenReturn(List.of(attendance()));
        when(attendanceRepository.countBySession(SESSION_ID)).thenReturn(1L);

        AttendancePage result = service.list(
            new ListSessionAttendanceQuery(SESSION_ID, 1, 10, "status,desc"), manager());

        assertThat(result.data()).containsExactly(attendance());
        assertThat(result.page()).isEqualTo(1);
        ArgumentCaptor<AttendancePageCriteria> criteria =
            ArgumentCaptor.forClass(AttendancePageCriteria.class);
        verify(attendanceRepository).findBySession(eq(SESSION_ID), criteria.capture());
        assertThat(criteria.getValue().sort()).isEqualTo(AttendanceSort.STATUS);
        assertThat(criteria.getValue().direction()).isEqualTo(AttendanceSortDirection.DESC);
    }

    @Test
    void getsAttendanceForSubjectOrScopedReader() {
        when(attendanceRepository.findById(ENROLLMENT_ID)).thenReturn(Optional.of(attendance()));
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session()));
        when(courseRepository.findActiveById(COURSE_ID)).thenReturn(Optional.of(course()));
        when(enrollmentRepository.findById(ENROLLMENT_ID)).thenReturn(Optional.of(enrollment()));

        assertThat(service.get(ENROLLMENT_ID, subject())).isEqualTo(attendance());
    }

    private void givenSessionCourseEnrollment() {
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session()));
        when(courseRepository.findActiveById(COURSE_ID)).thenReturn(Optional.of(course()));
        when(enrollmentRepository.findById(ENROLLMENT_ID)).thenReturn(Optional.of(enrollment()));
        when(attendanceRepository.findBySessionAndEnrollment(SESSION_ID, ENROLLMENT_ID))
            .thenReturn(Optional.empty());
    }

    private Attendance attendance() {
        return new Attendance(
            ENROLLMENT_ID, COURSE_ID, SESSION_ID, ENROLLMENT_ID,
            UUID.fromString("88888888-8888-8888-8888-888888888888"),
            AttendanceMethod.QR, AttendanceStatus.PRESENT, NOW, AttendanceSource.APP,
            SUBJECT_ID, NOW, NOW);
    }

    private Enrollment enrollment() {
        return new Enrollment(ENROLLMENT_ID, COURSE_ID, SUBJECT_ID, null,
            EnrollmentApplicationType.SELF, EnrollmentStatus.APPROVED, NOW.minusSeconds(60),
            NOW.minusSeconds(30), null, null, null, null, null, NOW.minusSeconds(60), NOW);
    }

    private CourseSession session() {
        return new CourseSession(SESSION_ID, COURSE_ID, 1, NOW.minusSeconds(60),
            NOW.plusSeconds(600), "room", NOW.minusSeconds(10), NOW.plusSeconds(300),
            CourseSessionStatus.OPEN, NOW.minusSeconds(120), NOW.minusSeconds(120));
    }

    private Course course() {
        return new Course(COURSE_ID, INSTITUTION_ID, "C-001", "Course", null, null,
            CourseEducationType.OFFLINE, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31),
            null, null, 30, null, null, false, null, CourseStatus.IN_PROGRESS,
            NOW.minusSeconds(120), NOW.minusSeconds(120), null);
    }

    private AuthenticatedUserContext subject() {
        return new AuthenticatedUserContext(SUBJECT_ID, "LOCAL",
            Set.of(new AuthenticatedRole("LEARNER", INSTITUTION_ID)));
    }

    private AuthenticatedUserContext manager() {
        return new AuthenticatedUserContext(MANAGER_ID, "LOCAL",
            Set.of(new AuthenticatedRole("INSTRUCTOR", INSTITUTION_ID)));
    }

    private static final class FixedAttendanceIdGenerator implements AttendanceIdGenerator {
        @Override
        public UUID generate() {
            return UUID.fromString("99999999-9999-9999-9999-999999999999");
        }
    }
}
