package com.adn.dabaeum.enrollment.application;

import com.adn.dabaeum.authorization.application.AuthorizationPolicy;
import com.adn.dabaeum.common.api.ApiErrorCode;
import com.adn.dabaeum.common.api.ApiException;
import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import com.adn.dabaeum.course.domain.Course;
import com.adn.dabaeum.course.domain.CourseInstructorRepository;
import com.adn.dabaeum.course.domain.CourseInstructorRole;
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
import com.adn.dabaeum.role.domain.UserRoleRepository;
import com.adn.dabaeum.user.domain.User;
import com.adn.dabaeum.user.domain.UserRepository;
import com.adn.dabaeum.user.domain.UserStatus;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DefaultEnrollmentApplicationService implements EnrollmentApplicationService {

    private final EnrollmentRepository enrollmentRepository;
    private final CourseRepository courseRepository;
    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final CourseInstructorRepository instructorRepository;
    private final EnrollmentIdGenerator idGenerator;
    private final AuthorizationPolicy authorizationPolicy;
    private final Clock clock;

    public DefaultEnrollmentApplicationService(
        EnrollmentRepository enrollmentRepository,
        CourseRepository courseRepository,
        UserRepository userRepository,
        UserRoleRepository userRoleRepository,
        CourseInstructorRepository instructorRepository,
        EnrollmentIdGenerator idGenerator,
        AuthorizationPolicy authorizationPolicy,
        Clock clock
    ) {
        this.enrollmentRepository = Objects.requireNonNull(enrollmentRepository, "enrollmentRepository");
        this.courseRepository = Objects.requireNonNull(courseRepository, "courseRepository");
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository");
        this.userRoleRepository = Objects.requireNonNull(userRoleRepository, "userRoleRepository");
        this.instructorRepository = Objects.requireNonNull(instructorRepository, "instructorRepository");
        this.idGenerator = Objects.requireNonNull(idGenerator, "idGenerator");
        this.authorizationPolicy = Objects.requireNonNull(authorizationPolicy, "authorizationPolicy");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    @Transactional
    public Enrollment createSelf(
        CreateEnrollmentCommand command,
        AuthenticatedUserContext context
    ) {
        if (command == null || command.courseId() == null || command.userId() == null
            || (command.applicationType() != null
                && command.applicationType() != EnrollmentApplicationType.SELF)) {
            throw validation("requestBody");
        }
        requireContextUser(context, command.userId());
        Course course = findCourse(command.courseId());
        requireActiveLearner(command.userId(), course.institutionId());
        return create(command.courseId(), command.userId(), null,
            EnrollmentApplicationType.SELF, course);
    }

    @Override
    @Transactional
    public Enrollment createProxy(
        CreateProxyEnrollmentCommand command,
        AuthenticatedUserContext context
    ) {
        if (command == null || command.courseId() == null || command.userId() == null) {
            throw validation("requestBody");
        }
        Course course = findCourse(command.courseId());
        authorizationPolicy.requireCourseManager(context, course.institutionId());
        requireActiveLearner(command.userId(), course.institutionId());
        return create(command.courseId(), command.userId(), context.userId(),
            EnrollmentApplicationType.ADMIN_PROXY, course);
    }

    @Override
    @Transactional(readOnly = true)
    public EnrollmentPage list(
        ListCourseEnrollmentsQuery query,
        AuthenticatedUserContext context
    ) {
        if (query == null || query.courseId() == null) {
            throw badRequest("query");
        }
        Course course = findCourse(query.courseId());
        boolean instructorAssigned = context != null
            && instructorRepository.existsByCourseIdAndUserId(course.id(), context.userId());
        authorizationPolicy.requireEnrollmentReader(
            context, course.institutionId(), instructorAssigned);
        if (query.page() < 0 || query.size() < 1 || query.size() > 100) {
            throw badRequest("page");
        }
        String[] sortParts = splitSort(query.sort());
        final EnrollmentSort sort;
        final EnrollmentSortDirection direction;
        try {
            sort = EnrollmentSort.fromApiValue(sortParts[0]);
            direction = EnrollmentSortDirection.fromApiValue(sortParts[1]);
        } catch (IllegalArgumentException exception) {
            throw badRequest("sort");
        }
        int offset;
        try {
            offset = Math.multiplyExact(query.page(), query.size());
        } catch (ArithmeticException exception) {
            throw badRequest("page");
        }
        List<Enrollment> data = enrollmentRepository.findPageByCourseId(
            new EnrollmentPageCriteria(query.courseId(), offset, query.size(), sort, direction));
        long total = enrollmentRepository.countByCourseId(query.courseId());
        int pages = total == 0 ? 0 : Math.toIntExact(((total - 1) / query.size()) + 1);
        return new EnrollmentPage(data, query.page(), query.size(), total, pages);
    }

    @Override
    @Transactional(readOnly = true)
    public Enrollment get(UUID enrollmentId, AuthenticatedUserContext context) {
        if (enrollmentId == null) {
            throw validation("enrollmentId");
        }
        Enrollment enrollment = enrollmentRepository.findById(enrollmentId)
            .orElseThrow(this::enrollmentNotFound);
        Course course = findCourse(enrollment.courseId());
        boolean instructorAssigned = context != null
            && instructorRepository.existsByCourseIdAndUserId(course.id(), context.userId());
        authorizationPolicy.requireEnrollmentSubjectOrReader(
            context, enrollment.userId(), course.institutionId(), instructorAssigned);
        return enrollment;
    }

    @Override
    @Transactional
    public Enrollment approve(UUID enrollmentId, AuthenticatedUserContext context) {
        Enrollment initial = findEnrollment(enrollmentId);
        Course lockedCourse = courseRepository.findActiveByIdForUpdate(initial.courseId())
            .orElseThrow(this::courseNotFound);
        Enrollment latest = enrollmentRepository.findByIdForUpdate(enrollmentId)
            .orElseThrow(this::enrollmentNotFound);
        requireDecisionManager(context, lockedCourse);
        requirePending(latest);
        long approved = enrollmentRepository.countApprovedByCourseId(lockedCourse.id());
        if (approved >= lockedCourse.capacity()) {
            throw new ApiException(HttpStatus.CONFLICT, ApiErrorCode.COURSE_CAPACITY_EXCEEDED,
                "Course capacity has been reached");
        }
        Instant now = clock.instant();
        Enrollment updated = new Enrollment(
            latest.id(), latest.courseId(), latest.userId(), latest.appliedBy(),
            latest.applicationType(), EnrollmentStatus.APPROVED, latest.appliedAt(), now,
            null, null, null, null, null, latest.createdAt(), now);
        return updateState(updated, latest.status());
    }

    @Override
    @Transactional
    public Enrollment reject(
        RejectEnrollmentCommand command,
        AuthenticatedUserContext context
    ) {
        if (command == null || command.enrollmentId() == null
            || command.reason() == null || command.reason().isBlank()
            || command.reason().trim().length() > 1000) {
            throw validation("reason");
        }
        Enrollment initial = findEnrollment(command.enrollmentId());
        Course course = findCourse(initial.courseId());
        Enrollment latest = enrollmentRepository.findByIdForUpdate(command.enrollmentId())
            .orElseThrow(this::enrollmentNotFound);
        requireDecisionManager(context, course);
        requirePending(latest);
        Instant now = clock.instant();
        Enrollment updated = new Enrollment(
            latest.id(), latest.courseId(), latest.userId(), latest.appliedBy(),
            latest.applicationType(), EnrollmentStatus.REJECTED, latest.appliedAt(), null,
            now, null, null, command.reason().trim(), null, latest.createdAt(), now);
        return updateState(updated, latest.status());
    }

    @Override
    @Transactional
    public Enrollment cancel(UUID enrollmentId, AuthenticatedUserContext context) {
        Enrollment initial = findEnrollment(enrollmentId);
        Course course = findCourse(initial.courseId());
        Enrollment latest = enrollmentRepository.findByIdForUpdate(enrollmentId)
            .orElseThrow(this::enrollmentNotFound);
        requireSubjectOrDecisionManager(context, latest, course);
        requirePending(latest);
        Instant now = clock.instant();
        Enrollment updated = new Enrollment(
            latest.id(), latest.courseId(), latest.userId(), latest.appliedBy(),
            latest.applicationType(), EnrollmentStatus.CANCELLED, latest.appliedAt(), null,
            null, now, null, null, null, latest.createdAt(), now);
        return updateState(updated, latest.status());
    }

    @Override
    @Transactional
    public Enrollment withdraw(UUID enrollmentId, AuthenticatedUserContext context) {
        Enrollment initial = findEnrollment(enrollmentId);
        Course course = findCourse(initial.courseId());
        Enrollment latest = enrollmentRepository.findByIdForUpdate(enrollmentId)
            .orElseThrow(this::enrollmentNotFound);
        requireSubjectOrDecisionManager(context, latest, course);
        if (latest.status() != EnrollmentStatus.APPROVED) {
            throw enrollmentStatusConflict();
        }
        Instant now = clock.instant();
        Enrollment updated = new Enrollment(
            latest.id(), latest.courseId(), latest.userId(), latest.appliedBy(),
            latest.applicationType(), EnrollmentStatus.WITHDRAWN, latest.appliedAt(),
            latest.approvedAt(), null, null, now, null, null, latest.createdAt(), now);
        return updateState(updated, latest.status());
    }

    private Enrollment create(
        UUID courseId,
        UUID userId,
        UUID appliedBy,
        EnrollmentApplicationType applicationType,
        Course course
    ) {
        Course locked = courseRepository.findActiveByIdForUpdate(course.id())
            .orElseThrow(this::courseNotFound);
        if (locked.status() != CourseStatus.RECRUITING) {
            throw new ApiException(HttpStatus.CONFLICT, ApiErrorCode.COURSE_STATUS_CONFLICT,
                "Course status does not allow enrollment");
        }
        if (enrollmentRepository.findActiveByCourseIdAndUserId(courseId, userId).isPresent()) {
            throw enrollmentConflict();
        }
        long approved = enrollmentRepository.countApprovedByCourseId(courseId);
        EnrollmentStatus status = approved >= locked.capacity()
            ? EnrollmentStatus.WAITLISTED : EnrollmentStatus.APPLIED;
        Instant now = clock.instant();
        Enrollment enrollment = new Enrollment(
            Objects.requireNonNull(idGenerator.generate(), "generated id"), courseId, userId,
            appliedBy, applicationType, status, now, null, null, null, null, null, null, now, now);
        try {
            enrollmentRepository.save(enrollment);
        } catch (DataIntegrityViolationException exception) {
            throw enrollmentConflict();
        }
        return enrollment;
    }

    private Enrollment findEnrollment(UUID enrollmentId) {
        if (enrollmentId == null) {
            throw validation("enrollmentId");
        }
        return enrollmentRepository.findById(enrollmentId)
            .orElseThrow(this::enrollmentNotFound);
    }

    private void requireDecisionManager(AuthenticatedUserContext context, Course course) {
        boolean mainInstructor = context != null
            && instructorRepository.existsByCourseIdAndUserIdAndRole(
                course.id(), context.userId(), CourseInstructorRole.MAIN);
        authorizationPolicy.requireEnrollmentDecisionManager(
            context, course.institutionId(), mainInstructor);
    }

    private void requireSubjectOrDecisionManager(
        AuthenticatedUserContext context,
        Enrollment enrollment,
        Course course
    ) {
        boolean mainInstructor = context != null
            && instructorRepository.existsByCourseIdAndUserIdAndRole(
                course.id(), context.userId(), CourseInstructorRole.MAIN);
        authorizationPolicy.requireEnrollmentSubjectOrDecisionManager(
            context, enrollment.userId(), course.institutionId(), mainInstructor);
    }

    private void requirePending(Enrollment enrollment) {
        if (enrollment.status() != EnrollmentStatus.APPLIED
            && enrollment.status() != EnrollmentStatus.WAITLISTED) {
            throw enrollmentStatusConflict();
        }
    }

    private Enrollment updateState(Enrollment updated, EnrollmentStatus expectedStatus) {
        try {
            if (!enrollmentRepository.updateState(updated, expectedStatus)) {
                throw enrollmentStatusConflict();
            }
        } catch (IllegalStateException exception) {
            throw enrollmentStatusConflict();
        }
        return updated;
    }

    private void requireContextUser(AuthenticatedUserContext context, UUID userId) {
        if (context == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, ApiErrorCode.UNAUTHORIZED,
                "Authentication required");
        }
        if (!Objects.equals(context.userId(), userId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, ApiErrorCode.INSTITUTION_SCOPE_FORBIDDEN,
                "User scope is not allowed");
        }
    }

    private void requireActiveLearner(UUID userId, UUID institutionId) {
        User user = userRepository.findById(userId).orElse(null);
        boolean learner = user != null && user.status() == UserStatus.ACTIVE
            && userRoleRepository.findByUserId(userId).stream()
                .anyMatch(assignment -> assignment.role() == UserRole.LEARNER
                    && (assignment.institutionId() == null
                        || assignment.institutionId().equals(institutionId)));
        if (!learner) {
            throw validation("userId");
        }
    }

    private Course findCourse(UUID courseId) {
        return courseRepository.findActiveById(courseId).orElseThrow(this::courseNotFound);
    }

    private String[] splitSort(String value) {
        if (value == null) {
            throw badRequest("sort");
        }
        String[] parts = value.split(",", -1);
        if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
            throw badRequest("sort");
        }
        return parts;
    }

    private ApiException validation(String detail) {
        return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, ApiErrorCode.VALIDATION_FAILED,
            "Request validation failed", List.of(detail));
    }

    private ApiException badRequest(String detail) {
        return new ApiException(HttpStatus.BAD_REQUEST, ApiErrorCode.BAD_REQUEST,
            "Invalid request", List.of(detail));
    }

    private ApiException courseNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, ApiErrorCode.COURSE_NOT_FOUND,
            "Course not found");
    }

    private ApiException enrollmentNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, ApiErrorCode.ENROLLMENT_NOT_FOUND,
            "Enrollment not found");
    }

    private ApiException enrollmentConflict() {
        return new ApiException(HttpStatus.CONFLICT, ApiErrorCode.ENROLLMENT_CONFLICT,
            "Enrollment conflict");
    }

    private ApiException enrollmentStatusConflict() {
        return new ApiException(HttpStatus.CONFLICT, ApiErrorCode.ENROLLMENT_STATUS_CONFLICT,
            "Enrollment status has changed");
    }
}
