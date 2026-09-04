package com.adn.dabaeum.enrollment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import com.adn.dabaeum.course.domain.CourseStatus;
import com.adn.dabaeum.enrollment.domain.Enrollment;
import com.adn.dabaeum.enrollment.domain.EnrollmentApplicationType;
import com.adn.dabaeum.enrollment.domain.EnrollmentRepository;
import com.adn.dabaeum.enrollment.domain.EnrollmentStatus;
import com.adn.dabaeum.role.domain.UserRoleRepository;
import com.adn.dabaeum.user.domain.UserRepository;
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
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EnrollmentLifecycleServiceTest {

    private static final UUID COURSE_ID = UUID.fromString(
        "33333333-3333-3333-3333-333333333333");
    private static final UUID INSTITUTION_ID = UUID.fromString(
        "22222222-2222-2222-2222-222222222222");
    private static final UUID LEARNER_ID = UUID.fromString(
        "11111111-1111-1111-1111-111111111111");
    private static final UUID MANAGER_ID = UUID.fromString(
        "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID ENROLLMENT_ID = UUID.fromString(
        "55555555-5555-5555-5555-555555555555");
    private static final Instant NOW = Instant.parse("2026-08-04T00:00:00Z");

    @Mock EnrollmentRepository enrollmentRepository;
    @Mock CourseRepository courseRepository;
    @Mock UserRepository userRepository;
    @Mock UserRoleRepository userRoleRepository;
    @Mock CourseInstructorRepository instructorRepository;

    private EnrollmentApplicationService service;
    private Course course;

    @BeforeEach
    void setUp() {
        service = new DefaultEnrollmentApplicationService(
            enrollmentRepository, courseRepository, userRepository, userRoleRepository,
            instructorRepository, () -> UUID.randomUUID(), new AuthorizationPolicy(),
            Clock.fixed(NOW, ZoneOffset.UTC));
        course = course(1);
        when(courseRepository.findActiveById(COURSE_ID)).thenReturn(Optional.of(course));
        when(courseRepository.findActiveByIdForUpdate(COURSE_ID))
            .thenReturn(Optional.of(course));
        when(enrollmentRepository.findByIdForUpdate(ENROLLMENT_ID))
            .thenReturn(Optional.of(enrollment(EnrollmentStatus.APPLIED)));
        when(enrollmentRepository.findById(ENROLLMENT_ID))
            .thenReturn(Optional.of(enrollment(EnrollmentStatus.APPLIED)));
        when(userRepository.findById(LEARNER_ID)).thenReturn(Optional.of(
            new com.adn.dabaeum.user.domain.User(LEARNER_ID, "학습자", "learner@example.com", null,
                LocalDate.of(1990, 1, 1), com.adn.dabaeum.user.domain.UserStatus.ACTIVE,
                null, NOW, NOW)));
    }

    @Test
    void approvesWaitlistedEnrollmentAfterCapacityCheck() {
        Enrollment waitlisted = enrollment(EnrollmentStatus.WAITLISTED);
        when(enrollmentRepository.findById(ENROLLMENT_ID)).thenReturn(Optional.of(waitlisted));
        when(enrollmentRepository.findByIdForUpdate(ENROLLMENT_ID))
            .thenReturn(Optional.of(waitlisted));
        when(instructorRepository.existsByCourseIdAndUserIdAndRole(
            COURSE_ID, MANAGER_ID, CourseInstructorRole.MAIN)).thenReturn(false);
        when(enrollmentRepository.countApprovedByCourseId(COURSE_ID)).thenReturn(0L);
        when(enrollmentRepository.updateState(any(), any())).thenReturn(true);

        Enrollment approved = service.approve(ENROLLMENT_ID, managerContext());

        assertThat(approved.status()).isEqualTo(EnrollmentStatus.APPROVED);
        assertThat(approved.approvedAt()).isEqualTo(NOW);
        verify(enrollmentRepository).updateState(any(), any());
    }

    @Test
    void rejectsWithTrimmedReasonAndPreservesPendingFields() {
        when(enrollmentRepository.updateState(any(), any())).thenReturn(true);

        Enrollment rejected = service.reject(
            new RejectEnrollmentCommand(ENROLLMENT_ID, "  요건 미충족  "), managerContext());

        assertThat(rejected.status()).isEqualTo(EnrollmentStatus.REJECTED);
        assertThat(rejected.rejectedAt()).isEqualTo(NOW);
        assertThat(rejected.rejectionReason()).isEqualTo("요건 미충족");
        verify(enrollmentRepository).updateState(any(), any());
    }

    @Test
    void rejectsInvalidReasonAndWrongState() {
        assertThatThrownBy(() -> service.reject(
            new RejectEnrollmentCommand(ENROLLMENT_ID, "  "), managerContext()))
            .isInstanceOfSatisfying(ApiException.class, error ->
                assertThat(error.code()).isEqualTo(ApiErrorCode.VALIDATION_FAILED));

        when(enrollmentRepository.findByIdForUpdate(ENROLLMENT_ID))
            .thenReturn(Optional.of(enrollment(EnrollmentStatus.APPROVED)));
        assertThatThrownBy(() -> service.reject(
            new RejectEnrollmentCommand(ENROLLMENT_ID, "사유"), managerContext()))
            .isInstanceOfSatisfying(ApiException.class, error ->
                assertThat(error.code()).isEqualTo(ApiErrorCode.ENROLLMENT_STATUS_CONFLICT));
    }

    @Test
    void cancelsPendingAndWithdrawsApprovedForSubject() {
        when(enrollmentRepository.updateState(any(), any())).thenReturn(true);
        Enrollment cancelled = service.cancel(ENROLLMENT_ID, learnerContext());
        assertThat(cancelled.status()).isEqualTo(EnrollmentStatus.CANCELLED);
        assertThat(cancelled.cancelledAt()).isEqualTo(NOW);

        Enrollment approved = enrollment(EnrollmentStatus.APPROVED);
        when(enrollmentRepository.findById(ENROLLMENT_ID)).thenReturn(Optional.of(approved));
        when(enrollmentRepository.findByIdForUpdate(ENROLLMENT_ID))
            .thenReturn(Optional.of(approved));
        Enrollment withdrawn = service.withdraw(ENROLLMENT_ID, learnerContext());
        assertThat(withdrawn.status()).isEqualTo(EnrollmentStatus.WITHDRAWN);
        assertThat(withdrawn.approvedAt()).isEqualTo(approved.approvedAt());
        assertThat(withdrawn.withdrawnAt()).isEqualTo(NOW);
    }

    @Test
    void approvalRejectsWhenCapacityReachedWithoutUpdate() {
        when(enrollmentRepository.countApprovedByCourseId(COURSE_ID)).thenReturn(1L);
        assertThatThrownBy(() -> service.approve(ENROLLMENT_ID, managerContext()))
            .isInstanceOfSatisfying(ApiException.class, error ->
                assertThat(error.code()).isEqualTo(ApiErrorCode.COURSE_CAPACITY_EXCEEDED));
        verify(enrollmentRepository, never()).updateState(any(), any());
    }

    @Test
    void expectedStatusGuardMapsConcurrentUpdateToConflict() {
        doThrow(new IllegalStateException("stale"))
            .when(enrollmentRepository).updateState(any(), any());
        assertThatThrownBy(() -> service.cancel(ENROLLMENT_ID, learnerContext()))
            .isInstanceOfSatisfying(ApiException.class, error ->
                assertThat(error.code()).isEqualTo(ApiErrorCode.ENROLLMENT_STATUS_CONFLICT));

        org.mockito.Mockito.reset(enrollmentRepository);
        when(enrollmentRepository.findById(ENROLLMENT_ID))
            .thenReturn(Optional.of(enrollment(EnrollmentStatus.APPLIED)));
        when(courseRepository.findActiveById(COURSE_ID)).thenReturn(Optional.of(course));
        when(instructorRepository.existsByCourseIdAndUserIdAndRole(
            COURSE_ID, LEARNER_ID, CourseInstructorRole.MAIN)).thenReturn(false);
        when(enrollmentRepository.findByIdForUpdate(ENROLLMENT_ID))
            .thenReturn(Optional.of(enrollment(EnrollmentStatus.APPLIED)));
        when(enrollmentRepository.updateState(any(), any())).thenReturn(false);
        assertThatThrownBy(() -> service.cancel(ENROLLMENT_ID, learnerContext()))
            .isInstanceOfSatisfying(ApiException.class, error ->
                assertThat(error.code()).isEqualTo(ApiErrorCode.ENROLLMENT_STATUS_CONFLICT));
    }

    @Test
    void rejectsUnassignedInstructorAndOtherLearner() {
        AuthenticatedUserContext instructor = new AuthenticatedUserContext(
            MANAGER_ID, "LOCAL", Set.of(new AuthenticatedRole("INSTRUCTOR", INSTITUTION_ID)));
        when(instructorRepository.existsByCourseIdAndUserIdAndRole(
            COURSE_ID, MANAGER_ID, CourseInstructorRole.MAIN)).thenReturn(false);
        assertThatThrownBy(() -> service.approve(ENROLLMENT_ID, instructor))
            .isInstanceOfSatisfying(ApiException.class, error ->
                assertThat(error.code()).isEqualTo(ApiErrorCode.INSTITUTION_SCOPE_FORBIDDEN));
        AuthenticatedUserContext other = new AuthenticatedUserContext(
            MANAGER_ID, "LOCAL", Set.of(new AuthenticatedRole("LEARNER", INSTITUTION_ID)));
        assertThatThrownBy(() -> service.cancel(ENROLLMENT_ID, other))
            .isInstanceOfSatisfying(ApiException.class, error ->
                assertThat(error.code()).isEqualTo(ApiErrorCode.INSTITUTION_SCOPE_FORBIDDEN));
    }

    private Enrollment enrollment(EnrollmentStatus status) {
        Instant approvedAt = status == EnrollmentStatus.APPROVED
            || status == EnrollmentStatus.WITHDRAWN ? NOW.minusSeconds(60) : null;
        Instant withdrawnAt = status == EnrollmentStatus.WITHDRAWN ? NOW : null;
        return new Enrollment(ENROLLMENT_ID, COURSE_ID, LEARNER_ID, null,
            EnrollmentApplicationType.SELF, status, NOW, approvedAt, null, null, withdrawnAt,
            null, null, NOW, NOW);
    }

    private Course course(int capacity) {
        return new Course(COURSE_ID, INSTITUTION_ID, "COURSE-1", "과정", null, null,
            CourseEducationType.HYBRID, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 1),
            null, null, capacity, null, null, false, (BigDecimal) null,
            CourseStatus.RECRUITING, NOW, NOW, null);
    }

    private AuthenticatedUserContext learnerContext() {
        return new AuthenticatedUserContext(LEARNER_ID, "LOCAL",
            Set.of(new AuthenticatedRole("LEARNER", INSTITUTION_ID)));
    }

    private AuthenticatedUserContext managerContext() {
        return new AuthenticatedUserContext(MANAGER_ID, "LOCAL",
            Set.of(new AuthenticatedRole("INSTITUTION_ADMIN", INSTITUTION_ID)));
    }
}
