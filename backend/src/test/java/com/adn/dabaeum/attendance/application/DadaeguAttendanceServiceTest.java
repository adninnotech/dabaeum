package com.adn.dabaeum.attendance.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.adn.dabaeum.attendance.domain.Attendance;
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
import com.adn.dabaeum.course.domain.CourseInstructorRole;
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
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

/**
 * 옵션 B: 강사 QR + 출석 시점 다대구 DID 재인증. 로그인 컨텍스트 없이 userId 로 수강신청을 찾아
 * QR_DADAEGU/PRESENT 로 기록하고, 다대구 identity 보유자는 일반 QR 경로에서 거부한다.
 */
@ExtendWith(MockitoExtension.class)
class DadaeguAttendanceServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-07T00:00:00Z");
    private static final UUID SESSION_ID = UUID.fromString(
        "22222222-2222-2222-2222-222222222222");
    private static final UUID COURSE_ID = UUID.fromString(
        "33333333-3333-3333-3333-333333333333");
    private static final UUID INSTITUTION_ID = UUID.fromString(
        "44444444-4444-4444-4444-444444444444");
    private static final UUID SUBJECT_ID = UUID.fromString(
        "55555555-5555-5555-5555-555555555555");
    private static final UUID INSTRUCTOR_ID = UUID.fromString(
        "66666666-6666-6666-6666-666666666666");
    private static final UUID ENROLLMENT_ID = UUID.fromString(
        "77777777-7777-7777-7777-777777777777");
    private static final UUID TOKEN_ID = UUID.fromString(
        "88888888-8888-8888-8888-888888888888");
    private static final UUID ATTENDANCE_ID = UUID.fromString(
        "99999999-9999-9999-9999-999999999999");
    private static final String QR_TOKEN = "a".repeat(32);

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
            new AuthorizationPolicy(),
            Clock.fixed(NOW, ZoneOffset.UTC),
            () -> ATTENDANCE_ID
        );
    }

    /** 학생이 QR 을 찍는 순간 이미 검증된 결과. recordDadaegu 는 이걸 그대로 받는다. */
    private ValidatedAttendanceQrToken validatedToken() {
        return new ValidatedAttendanceQrToken(
            TOKEN_ID, SESSION_ID, INSTRUCTOR_ID, NOW.minusSeconds(1), NOW.plusSeconds(59));
    }

    private RecordDadaeguAttendanceCommand command() {
        return new RecordDadaeguAttendanceCommand(SUBJECT_ID, validatedToken());
    }

    /** D안: 학생 폰이 강사 QR(URL)을 찍어 다대구 인증을 마치면 서버가 대신 기록한다. */
    @Test
    void recordsQrDadaeguPresentWithInstructorAsCreator() {
        givenOpenSessionAndCourse();
        when(enrollmentRepository.findActiveByCourseIdAndUserId(COURSE_ID, SUBJECT_ID))
            .thenReturn(Optional.of(enrollment(EnrollmentStatus.APPROVED)));
        when(attendanceRepository.findBySessionAndEnrollment(SESSION_ID, ENROLLMENT_ID))
            .thenReturn(Optional.empty());

        Attendance result = service.recordDadaegu(
            command());

        assertThat(result.id()).isEqualTo(ATTENDANCE_ID);
        assertThat(result.enrollmentId()).isEqualTo(ENROLLMENT_ID);
        assertThat(result.attendanceMethod()).isEqualTo(AttendanceMethod.QR_DADAEGU);
        assertThat(result.status()).isEqualTo(AttendanceStatus.PRESENT);
        assertThat(result.source()).isEqualTo(AttendanceSource.APP);
        assertThat(result.qrTokenId()).isEqualTo(TOKEN_ID);
        assertThat(result.checkedAt()).isEqualTo(NOW);
        // 학생이 아니라 토큰 발급자다. 학생은 로그인하지 않아 남길 주체가 이것뿐이다.
        assertThat(result.createdBy()).isEqualTo(INSTRUCTOR_ID);
        verify(attendanceRepository).save(result);
    }

    @Test
    void recordDadaeguFailsWith404WhenUserHasNoActiveEnrollmentInSessionCourse() {
        givenOpenSessionAndCourse();
        when(enrollmentRepository.findActiveByCourseIdAndUserId(COURSE_ID, SUBJECT_ID))
            .thenReturn(Optional.empty());

        assertApi(() -> service.recordDadaegu(
                command()),
            HttpStatus.NOT_FOUND, ApiErrorCode.ENROLLMENT_NOT_FOUND);

        verifyNoInteractions(qrTokenService);
        verify(attendanceRepository, never()).save(any());
    }

    @Test
    void recordDadaeguRejectsNotApprovedEnrollmentBeforeTouchingToken() {
        givenOpenSessionAndCourse();
        when(enrollmentRepository.findActiveByCourseIdAndUserId(COURSE_ID, SUBJECT_ID))
            .thenReturn(Optional.of(enrollment(EnrollmentStatus.APPLIED)));

        assertApi(() -> service.recordDadaegu(
                command()),
            HttpStatus.CONFLICT, ApiErrorCode.ENROLLMENT_STATUS_CONFLICT);

        verifyNoInteractions(qrTokenService);
        verify(attendanceRepository, never()).save(any());
    }

    @Test
    void recordDadaeguRejectsDuplicateWith409() {
        givenOpenSessionAndCourse();
        when(enrollmentRepository.findActiveByCourseIdAndUserId(COURSE_ID, SUBJECT_ID))
            .thenReturn(Optional.of(enrollment(EnrollmentStatus.APPROVED)));
        when(attendanceRepository.findBySessionAndEnrollment(SESSION_ID, ENROLLMENT_ID))
            .thenReturn(Optional.of(existingAttendance()));

        assertApi(() -> service.recordDadaegu(
                command()),
            HttpStatus.CONFLICT, ApiErrorCode.ATTENDANCE_CONFLICT);

        verify(attendanceRepository, never()).save(any());
    }

    @Test
    void recordDadaeguRejectsNullCommandWith422() {
        assertApi(() -> service.recordDadaegu(null),
            HttpStatus.UNPROCESSABLE_CONTENT, ApiErrorCode.VALIDATION_FAILED);
        assertApi(() -> service.recordDadaegu(
                new RecordDadaeguAttendanceCommand(null, validatedToken())),
            HttpStatus.UNPROCESSABLE_CONTENT, ApiErrorCode.VALIDATION_FAILED);
        verifyNoInteractions(sessionRepository, qrTokenService, attendanceRepository);
    }

    /** 이미 검증된 토큰을 받으므로 다시 검증하지 않는다 — 다시 보면 인증에 시간을 끈 학생이 거부된다. */
    @Test
    void recordDadaeguDoesNotRevalidateTheToken() {
        givenOpenSessionAndCourse();
        when(enrollmentRepository.findActiveByCourseIdAndUserId(COURSE_ID, SUBJECT_ID))
            .thenReturn(Optional.of(enrollment(EnrollmentStatus.APPROVED)));
        when(attendanceRepository.findBySessionAndEnrollment(SESSION_ID, ENROLLMENT_ID))
            .thenReturn(Optional.empty());

        service.recordDadaegu(command());

        verifyNoInteractions(qrTokenService);
    }

    /** 일반 QR 경로는 닫혔다. 다대구 회원은 /attend(QR_DADAEGU), 그 밖은 강사·관리자 기록(ADMIN)이다. */
    @Test
    void legacyQrPathIsClosedForEveryLearner() {
        assertApi(() -> service.record(new RecordAttendanceCommand(
                SESSION_ID, ENROLLMENT_ID, AttendanceMethod.QR, AttendanceStatus.PRESENT,
                null, AttendanceSource.APP, QR_TOKEN), subject()),
            HttpStatus.UNPROCESSABLE_CONTENT, ApiErrorCode.ATTENDANCE_METHOD_NOT_SUPPORTED);

        verifyNoInteractions(qrTokenService, attendanceRepository);
    }

    private void assertApi(ThrowingCallable call, HttpStatus status, ApiErrorCode code) {
        assertThatThrownBy(call).isInstanceOfSatisfying(ApiException.class, exception -> {
            assertThat(exception.status()).isEqualTo(status);
            assertThat(exception.code()).isEqualTo(code);
        });
    }

    private void givenOpenSessionAndCourse() {
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session()));
        when(courseRepository.findActiveById(COURSE_ID)).thenReturn(Optional.of(course()));
    }

    private Attendance existingAttendance() {
        return new Attendance(
            UUID.fromString("66666666-6666-6666-6666-666666666666"), COURSE_ID, SESSION_ID,
            ENROLLMENT_ID, TOKEN_ID, AttendanceMethod.QR, AttendanceStatus.PRESENT, NOW,
            AttendanceSource.APP, SUBJECT_ID, NOW, NOW);
    }

    private Enrollment enrollment(EnrollmentStatus status) {
        return new Enrollment(ENROLLMENT_ID, COURSE_ID, SUBJECT_ID, null,
            EnrollmentApplicationType.SELF, status, NOW.minusSeconds(60),
            status == EnrollmentStatus.APPROVED ? NOW.minusSeconds(30) : null,
            null, null, null, null, null, NOW.minusSeconds(60), NOW);
    }

    private CourseSession session() {
        return new CourseSession(SESSION_ID, COURSE_ID, 1, NOW.minusSeconds(60),
            NOW.plusSeconds(600), "room", NOW.minusSeconds(10), NOW.plusSeconds(300),
            CourseSessionStatus.OPEN, NOW.minusSeconds(120), NOW.minusSeconds(120), null);
    }

    private Course course() {
        return new Course(COURSE_ID, INSTITUTION_ID, "C-001", "Course", null, null,
            CourseEducationType.OFFLINE, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31),
            null, null, 30, null, null, false, null, CourseStatus.IN_PROGRESS,
            NOW.minusSeconds(120), NOW.minusSeconds(120), null);
    }

    private AuthenticatedUserContext instructor() {
        return new AuthenticatedUserContext(INSTRUCTOR_ID, "LOCAL",
            Set.of(new AuthenticatedRole("INSTRUCTOR", INSTITUTION_ID)));
    }

    /** requireManager 는 MAIN 강사 배정 여부를 리포지토리에 묻는다. */
    private void givenMainInstructor() {
        when(instructorRepository.existsByCourseIdAndUserIdAndRole(
            COURSE_ID, INSTRUCTOR_ID, CourseInstructorRole.MAIN)).thenReturn(true);
    }

    private AuthenticatedUserContext subject() {
        return new AuthenticatedUserContext(SUBJECT_ID, "LOCAL",
            Set.of(new AuthenticatedRole("LEARNER", INSTITUTION_ID)));
    }
}
