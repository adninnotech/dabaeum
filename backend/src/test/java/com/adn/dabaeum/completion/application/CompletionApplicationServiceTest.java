package com.adn.dabaeum.completion.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.adn.dabaeum.attendance.application.AttendanceMetrics;
import com.adn.dabaeum.attendance.application.AttendanceMetricsQuery;
import com.adn.dabaeum.authorization.application.AuthorizationPolicy;
import com.adn.dabaeum.common.api.ApiErrorCode;
import com.adn.dabaeum.common.api.ApiException;
import com.adn.dabaeum.common.security.AuthenticatedRole;
import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import com.adn.dabaeum.completion.domain.Completion;
import com.adn.dabaeum.completion.domain.CompletionConfirmedEvent;
import com.adn.dabaeum.completion.domain.CompletionOutboxRepository;
import com.adn.dabaeum.completion.domain.CompletionRepository;
import com.adn.dabaeum.completion.domain.CompletionStatus;
import com.adn.dabaeum.course.domain.Course;
import com.adn.dabaeum.course.domain.CourseEducationType;
import com.adn.dabaeum.course.domain.CourseInstructorRepository;
import com.adn.dabaeum.course.domain.CourseRepository;
import com.adn.dabaeum.course.domain.CourseStatus;
import com.adn.dabaeum.course.domain.CourseSessionRepository;
import com.adn.dabaeum.enrollment.domain.Enrollment;
import com.adn.dabaeum.enrollment.domain.EnrollmentApplicationType;
import com.adn.dabaeum.enrollment.domain.EnrollmentRepository;
import com.adn.dabaeum.enrollment.domain.EnrollmentStatus;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CompletionApplicationServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-05T00:00:00Z");
    private static final UUID COMPLETION_ID = UUID.fromString(
        "11111111-1111-1111-1111-111111111111");
    private static final UUID ENROLLMENT_ID = UUID.fromString(
        "22222222-2222-2222-2222-222222222222");
    private static final UUID COURSE_ID = UUID.fromString(
        "33333333-3333-3333-3333-333333333333");
    private static final UUID INSTITUTION_ID = UUID.fromString(
        "44444444-4444-4444-4444-444444444444");
    private static final UUID SUBJECT_ID = UUID.fromString(
        "55555555-5555-5555-5555-555555555555");
    private static final UUID MANAGER_ID = UUID.fromString(
        "66666666-6666-6666-6666-666666666666");

    @Mock CompletionRepository completionRepository;
    @Mock CompletionOutboxRepository outboxRepository;
    @Mock CompletionIdGenerator idGenerator;
    @Mock EnrollmentRepository enrollmentRepository;
    @Mock CourseRepository courseRepository;
    @Mock CourseInstructorRepository instructorRepository;
    @Mock CourseSessionRepository sessionRepository;
    @Mock AttendanceMetricsQuery metricsQuery;

    private CompletionApplicationService service;

    @BeforeEach
    void setUp() {
        service = new DefaultCompletionApplicationService(
            completionRepository, outboxRepository, idGenerator, enrollmentRepository,
            courseRepository, instructorRepository, sessionRepository, metricsQuery,
            new AuthorizationPolicy(),
            org.mockito.Mockito.mock(com.adn.dabaeum.credential.domain.CredentialGroupRepository.class),
            org.mockito.Mockito.mock(com.adn.dabaeum.correction.domain.AdminCorrectionRepository.class),
            Clock.fixed(NOW, ZoneOffset.UTC));
        when(enrollmentRepository.findById(ENROLLMENT_ID)).thenReturn(Optional.of(enrollment()));
        when(courseRepository.findActiveById(COURSE_ID)).thenReturn(Optional.of(course()));
        when(instructorRepository.existsByCourseIdAndUserIdAndRole(any(), any(), any()))
            .thenReturn(true);
        when(sessionRepository.existsAttendanceIneligibleByCourseId(COURSE_ID, NOW))
            .thenReturn(false);
        when(metricsQuery.calculate(ENROLLMENT_ID, NOW)).thenReturn(metrics());
    }

    @Test
    void initialEvaluateLocksEnrollmentAndInsertsEligibleCompletion() {
        when(completionRepository.findByEnrollmentId(ENROLLMENT_ID)).thenReturn(Optional.empty());
        when(enrollmentRepository.findByIdForUpdate(ENROLLMENT_ID))
            .thenReturn(Optional.of(enrollment()));
        when(idGenerator.generate()).thenReturn(COMPLETION_ID);

        Completion result = service.evaluate(new EvaluateCompletionCommand(
            ENROLLMENT_ID, new BigDecimal("66.67"), 120, null, null), manager());

        assertThat(result.status()).isEqualTo(CompletionStatus.ELIGIBLE);
        assertThat(result.attendanceRate()).isEqualByComparingTo("66.67");
        verify(enrollmentRepository).findByIdForUpdate(ENROLLMENT_ID);
        verify(completionRepository).save(result);
    }

    @Test
    void reEvaluateLocksCompletionAndUsesExpectedStatusUpdate() {
        Completion current = eligible();
        when(completionRepository.findByEnrollmentId(ENROLLMENT_ID)).thenReturn(Optional.of(current));
        when(completionRepository.findByEnrollmentIdForUpdate(ENROLLMENT_ID))
            .thenReturn(Optional.of(current));
        when(completionRepository.updateEvaluation(any(), eq(CompletionStatus.ELIGIBLE)))
            .thenReturn(true);

        Completion result = service.evaluate(new EvaluateCompletionCommand(
            ENROLLMENT_ID, new BigDecimal("66.67"), 120, null, "출석 기준 미달"), manager());

        assertThat(result.status()).isEqualTo(CompletionStatus.NOT_COMPLETED);
        verify(completionRepository).updateEvaluation(any(), eq(CompletionStatus.ELIGIBLE));
    }

    // 호출자가 보낸 지표는 참고용이다. 서버가 출결 기록과 과정 정책으로 계산한 값으로 판정하고
    // 그 값을 결과에 담는다. 값을 아예 보내지 않아도 된다.
    @Test
    void evaluatesWithServerCalculatedMetricsRegardlessOfClientValues() {
        when(idGenerator.generate()).thenReturn(COMPLETION_ID);
        when(completionRepository.findByEnrollmentId(ENROLLMENT_ID)).thenReturn(Optional.empty());
        when(enrollmentRepository.findByIdForUpdate(ENROLLMENT_ID))
            .thenReturn(Optional.of(enrollment()));

        Completion mismatched = service.evaluate(new EvaluateCompletionCommand(
            ENROLLMENT_ID, new BigDecimal("1.00"), 5, new BigDecimal("1.00"), null), manager());
        assertThat(mismatched.status()).isEqualTo(CompletionStatus.ELIGIBLE);
        assertThat(mismatched.attendanceRate()).isEqualByComparingTo(new BigDecimal("66.67"));
        assertThat(mismatched.completedMinutes()).isEqualTo(120);

        Completion omitted = service.evaluate(new EvaluateCompletionCommand(
            ENROLLMENT_ID, null, null, null, null), manager());
        assertThat(omitted.status()).isEqualTo(CompletionStatus.ELIGIBLE);
        assertThat(omitted.attendanceRate()).isEqualByComparingTo(new BigDecimal("66.67"));
        assertThat(omitted.completedMinutes()).isEqualTo(120);
    }

    @Test
    void getsForSubjectAndRejectsMissingCompletion() {
        Completion current = eligible();
        when(completionRepository.findByEnrollmentId(ENROLLMENT_ID)).thenReturn(Optional.of(current));
        assertThat(service.get(ENROLLMENT_ID, subject()).status()).isEqualTo(CompletionStatus.ELIGIBLE);

        when(completionRepository.findByEnrollmentId(ENROLLMENT_ID)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.get(ENROLLMENT_ID, subject()))
            .isInstanceOf(ApiException.class)
            .extracting("code").isEqualTo(ApiErrorCode.COMPLETION_NOT_FOUND);
    }

    @Test
    void confirmsEligibleAndWritesOneOutboxEventAndRejectsSecondConfirm() {
        Completion current = eligible();
        when(completionRepository.findByEnrollmentIdForUpdate(ENROLLMENT_ID))
            .thenReturn(Optional.of(current));
        when(completionRepository.confirm(any(), eq(CompletionStatus.ELIGIBLE))).thenReturn(true);

        Completion result = service.confirm(ENROLLMENT_ID, manager());

        assertThat(result.status()).isEqualTo(CompletionStatus.COMPLETED);
        assertThat(result.confirmedBy()).isEqualTo(MANAGER_ID);
        verify(outboxRepository).save(any(CompletionConfirmedEvent.class));

        when(completionRepository.findByEnrollmentIdForUpdate(ENROLLMENT_ID))
            .thenReturn(Optional.of(result));
        assertThatThrownBy(() -> service.confirm(ENROLLMENT_ID, manager()))
            .isInstanceOf(ApiException.class)
            .extracting("code").isEqualTo(ApiErrorCode.COMPLETION_STATUS_CONFLICT);
        verify(outboxRepository).save(any(CompletionConfirmedEvent.class));
    }

    private AttendanceMetrics metrics() {
        return new AttendanceMetrics(ENROLLMENT_ID, 3, 1, 1, 1, 0,
            new BigDecimal("66.67"), 120);
    }

    private Completion eligible() {
        return new Completion(COMPLETION_ID, ENROLLMENT_ID, CompletionStatus.ELIGIBLE,
            new BigDecimal("66.67"), 120, null, NOW, null, null, null, null, NOW, NOW);
    }

    private Course course() {
        return new Course(COURSE_ID, INSTITUTION_ID, "C-001", "Course", null, null,
            CourseEducationType.OFFLINE, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31),
            null, null, 30, null, null, false, null, CourseStatus.IN_PROGRESS, NOW, NOW, null);
    }

    private Enrollment enrollment() {
        return new Enrollment(ENROLLMENT_ID, COURSE_ID, SUBJECT_ID, null,
            EnrollmentApplicationType.SELF, EnrollmentStatus.APPROVED, NOW.minusSeconds(60),
            NOW.minusSeconds(30), null, null, null, null, null, NOW.minusSeconds(60), NOW);
    }

    private AuthenticatedUserContext manager() {
        return new AuthenticatedUserContext(MANAGER_ID, "LOCAL",
            Set.of(new AuthenticatedRole("INSTRUCTOR", INSTITUTION_ID)));
    }

    private AuthenticatedUserContext subject() {
        return new AuthenticatedUserContext(SUBJECT_ID, "LOCAL",
            Set.of(new AuthenticatedRole("LEARNER", INSTITUTION_ID)));
    }
}
