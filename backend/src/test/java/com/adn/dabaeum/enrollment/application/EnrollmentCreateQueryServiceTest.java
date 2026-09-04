package com.adn.dabaeum.enrollment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
import com.adn.dabaeum.course.domain.CourseRepository;
import com.adn.dabaeum.course.domain.CourseStatus;
import com.adn.dabaeum.enrollment.domain.Enrollment;
import com.adn.dabaeum.enrollment.domain.EnrollmentApplicationType;
import com.adn.dabaeum.enrollment.domain.EnrollmentPageCriteria;
import com.adn.dabaeum.enrollment.domain.EnrollmentRepository;
import com.adn.dabaeum.enrollment.domain.EnrollmentSort;
import com.adn.dabaeum.enrollment.domain.EnrollmentSortDirection;
import com.adn.dabaeum.enrollment.domain.EnrollmentStatus;
import com.adn.dabaeum.role.domain.UserRole;
import com.adn.dabaeum.role.domain.UserRoleAssignment;
import com.adn.dabaeum.role.domain.UserRoleRepository;
import com.adn.dabaeum.user.domain.User;
import com.adn.dabaeum.user.domain.UserRepository;
import com.adn.dabaeum.user.domain.UserStatus;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EnrollmentCreateQueryServiceTest {

    private static final UUID COURSE_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID INSTITUTION_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID LEARNER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ADMIN_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID ENROLLMENT_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final Instant NOW = Instant.parse("2026-08-04T00:00:00Z");

    @Mock EnrollmentRepository enrollmentRepository;
    @Mock CourseRepository courseRepository;
    @Mock UserRepository userRepository;
    @Mock UserRoleRepository userRoleRepository;
    @Mock CourseInstructorRepository instructorRepository;
    @Mock EnrollmentIdGenerator idGenerator;

    private EnrollmentApplicationService service;
    private Course course;

    @BeforeEach
    void setUp() {
        service = new DefaultEnrollmentApplicationService(
            enrollmentRepository, courseRepository, userRepository, userRoleRepository,
            instructorRepository, idGenerator, new AuthorizationPolicy(),
            Clock.fixed(NOW, ZoneOffset.UTC));
        course = course(CourseStatus.RECRUITING, 1);
        when(courseRepository.findActiveById(COURSE_ID)).thenReturn(Optional.of(course));
        when(courseRepository.findActiveByIdForUpdate(COURSE_ID)).thenReturn(Optional.of(course));
        when(userRepository.findById(LEARNER_ID)).thenReturn(Optional.of(activeUser(LEARNER_ID)));
        when(userRoleRepository.findByUserId(LEARNER_ID))
            .thenReturn(List.of(role(LEARNER_ID, UserRole.LEARNER)));
        when(enrollmentRepository.findActiveByCourseIdAndUserId(COURSE_ID, LEARNER_ID))
            .thenReturn(Optional.empty());
        when(enrollmentRepository.countApprovedByCourseId(COURSE_ID)).thenReturn(0L);
        when(idGenerator.generate()).thenReturn(ENROLLMENT_ID);
    }

    @Test
    void createsSelfEnrollmentWithContextIdentityAndSelfType() {
        Enrollment created = service.createSelf(
            new CreateEnrollmentCommand(COURSE_ID, LEARNER_ID, EnrollmentApplicationType.SELF),
            learnerContext());

        assertThat(created.id()).isEqualTo(ENROLLMENT_ID);
        assertThat(created.appliedBy()).isNull();
        assertThat(created.applicationType()).isEqualTo(EnrollmentApplicationType.SELF);
        assertThat(created.status()).isEqualTo(EnrollmentStatus.APPLIED);
    }

    @Test
    void createsSelfEnrollmentForGlobalLearnerAcrossInstitutions() {
        when(userRoleRepository.findByUserIdAndInstitution(
            LEARNER_ID,
            INSTITUTION_ID
        )).thenReturn(List.of());
        when(userRoleRepository.findByUserId(LEARNER_ID)).thenReturn(List.of(
            new UserRoleAssignment(
                UUID.randomUUID(),
                LEARNER_ID,
                null,
                UserRole.LEARNER,
                NOW
            )
        ));

        Enrollment created = service.createSelf(
            new CreateEnrollmentCommand(
                COURSE_ID,
                LEARNER_ID,
                EnrollmentApplicationType.SELF
            ),
            learnerContext()
        );

        assertThat(created.id()).isEqualTo(ENROLLMENT_ID);
        assertThat(created.userId()).isEqualTo(LEARNER_ID);
    }

    @Test
    void rejectsSelfUserSpoofAndProxyExternalTypes() {
        assertThatThrownBy(() -> service.createSelf(
            new CreateEnrollmentCommand(COURSE_ID, ADMIN_ID, EnrollmentApplicationType.SELF),
            learnerContext()))
            .isInstanceOf(ApiException.class)
            .satisfies(error -> assertThat(((ApiException) error).status().value()).isEqualTo(403));
        assertThatThrownBy(() -> service.createSelf(
            new CreateEnrollmentCommand(COURSE_ID, LEARNER_ID, EnrollmentApplicationType.ADMIN_PROXY),
            learnerContext()))
            .isInstanceOf(ApiException.class)
            .satisfies(error -> assertThat(((ApiException) error).code())
                .isEqualTo(ApiErrorCode.VALIDATION_FAILED));
        verify(enrollmentRepository, never()).save(any());
    }

    @Test
    void createsProxyWithPrincipalAppliedByAndWaitlistsAtCapacity() {
        when(userRepository.findById(LEARNER_ID)).thenReturn(Optional.of(activeUser(LEARNER_ID)));
        when(enrollmentRepository.countApprovedByCourseId(COURSE_ID)).thenReturn(1L);

        Enrollment created = service.createProxy(
            new CreateProxyEnrollmentCommand(COURSE_ID, LEARNER_ID), adminContext());

        assertThat(created.applicationType()).isEqualTo(EnrollmentApplicationType.ADMIN_PROXY);
        assertThat(created.appliedBy()).isEqualTo(ADMIN_ID);
        assertThat(created.status()).isEqualTo(EnrollmentStatus.WAITLISTED);
    }

    @Test
    void requiresActiveLearnerAndManagerScopeForProxy() {
        when(userRepository.findById(LEARNER_ID)).thenReturn(Optional.of(
            new User(LEARNER_ID, "휴면", "dormant@example.com", null,
                LocalDate.of(1990, 1, 1), UserStatus.DORMANT, null, NOW, NOW)));
        assertThatThrownBy(() -> service.createProxy(
            new CreateProxyEnrollmentCommand(COURSE_ID, LEARNER_ID), adminContext()))
            .isInstanceOf(ApiException.class)
            .satisfies(error -> assertThat(((ApiException) error).code())
                .isEqualTo(ApiErrorCode.VALIDATION_FAILED));

        assertThatThrownBy(() -> service.createProxy(
            new CreateProxyEnrollmentCommand(COURSE_ID, LEARNER_ID), learnerContext()))
            .isInstanceOf(ApiException.class)
            .satisfies(error -> assertThat(((ApiException) error).status().value()).isEqualTo(403));
    }

    @Test
    void rechecksLockedCourseStatusAndMapsDuplicateSave() {
        when(courseRepository.findActiveByIdForUpdate(COURSE_ID))
            .thenReturn(Optional.of(course(CourseStatus.COMPLETED, 1)));
        assertThatThrownBy(() -> service.createSelf(
            new CreateEnrollmentCommand(COURSE_ID, LEARNER_ID, null), learnerContext()))
            .isInstanceOf(ApiException.class)
            .satisfies(error -> assertThat(((ApiException) error).code())
                .isEqualTo(ApiErrorCode.COURSE_STATUS_CONFLICT));

        when(courseRepository.findActiveByIdForUpdate(COURSE_ID)).thenReturn(Optional.of(course));
        when(enrollmentRepository.findActiveByCourseIdAndUserId(COURSE_ID, LEARNER_ID))
            .thenReturn(Optional.empty());
        when(enrollmentRepository.countApprovedByCourseId(COURSE_ID)).thenReturn(0L);
        org.mockito.Mockito.doThrow(new DataIntegrityViolationException("duplicate"))
            .when(enrollmentRepository).save(any());
        assertThatThrownBy(() -> service.createSelf(
            new CreateEnrollmentCommand(COURSE_ID, LEARNER_ID, null), learnerContext()))
            .isInstanceOf(ApiException.class)
            .satisfies(error -> assertThat(((ApiException) error).code())
                .isEqualTo(ApiErrorCode.ENROLLMENT_CONFLICT));
    }

    @Test
    void listsForInstructorAndGetsForSubjectButRejectsOtherLearner() {
        when(instructorRepository.existsByCourseIdAndUserId(COURSE_ID, LEARNER_ID)).thenReturn(true);
        when(enrollmentRepository.findPageByCourseId(any())).thenReturn(List.of());
        EnrollmentPage page = service.list(
            new ListCourseEnrollmentsQuery(COURSE_ID, 0, 20, "createdAt,desc"), instructorContext());
        assertThat(page.totalElements()).isZero();
        verify(enrollmentRepository).findPageByCourseId(new EnrollmentPageCriteria(
            COURSE_ID, 0, 20, EnrollmentSort.CREATED_AT, EnrollmentSortDirection.DESC));

        Enrollment enrollment = enrollment(LEARNER_ID);
        when(enrollmentRepository.findById(ENROLLMENT_ID)).thenReturn(Optional.of(enrollment));
        assertThat(service.get(ENROLLMENT_ID, instructorContext())).isEqualTo(enrollment);
        AuthenticatedUserContext other = new AuthenticatedUserContext(
            ADMIN_ID, "LOCAL", Set.of(new AuthenticatedRole("LEARNER", INSTITUTION_ID)));
        assertThatThrownBy(() -> service.get(ENROLLMENT_ID, other))
            .isInstanceOf(ApiException.class)
            .satisfies(error -> assertThat(((ApiException) error).status().value()).isEqualTo(403));
    }

    private AuthenticatedUserContext learnerContext() {
        return new AuthenticatedUserContext(LEARNER_ID, "LOCAL",
            Set.of(new AuthenticatedRole("LEARNER", INSTITUTION_ID)));
    }

    private AuthenticatedUserContext adminContext() {
        return new AuthenticatedUserContext(ADMIN_ID, "LOCAL",
            Set.of(new AuthenticatedRole("INSTITUTION_ADMIN", INSTITUTION_ID)));
    }

    private AuthenticatedUserContext instructorContext() {
        return new AuthenticatedUserContext(LEARNER_ID, "LOCAL",
            Set.of(new AuthenticatedRole("INSTRUCTOR", INSTITUTION_ID)));
    }

    private UserRoleAssignment role(UUID userId, UserRole value) {
        return new UserRoleAssignment(UUID.randomUUID(), userId, INSTITUTION_ID, value, NOW);
    }

    private User activeUser(UUID userId) {
        return new User(userId, "학습자", userId + "@example.com", null,
            LocalDate.of(1990, 1, 1), UserStatus.ACTIVE, null, NOW, NOW);
    }

    private Enrollment enrollment(UUID userId) {
        return new Enrollment(ENROLLMENT_ID, COURSE_ID, userId, null,
            EnrollmentApplicationType.SELF, EnrollmentStatus.APPLIED, NOW, null, null,
            null, null, null, null, NOW, NOW);
    }

    private Course course(CourseStatus status, int capacity) {
        return new Course(COURSE_ID, INSTITUTION_ID, "COURSE-1", "과정", null, null,
            CourseEducationType.HYBRID, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 1),
            null, null, capacity, null, null, false, (BigDecimal) null, status, NOW, NOW, null);
    }
}
