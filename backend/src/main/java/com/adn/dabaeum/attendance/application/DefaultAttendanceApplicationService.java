package com.adn.dabaeum.attendance.application;

import com.adn.dabaeum.attendance.domain.Attendance;
import com.adn.dabaeum.attendance.domain.AttendanceAdjustment;
import com.adn.dabaeum.attendance.domain.AttendanceAdjustmentRepository;
import com.adn.dabaeum.attendance.domain.AttendanceMethod;
import com.adn.dabaeum.attendance.domain.AttendancePageCriteria;
import com.adn.dabaeum.attendance.domain.AttendanceRepository;
import com.adn.dabaeum.attendance.domain.AttendanceSort;
import com.adn.dabaeum.attendance.domain.AttendanceSortDirection;
import com.adn.dabaeum.attendance.domain.AttendanceSource;
import com.adn.dabaeum.attendance.domain.AttendanceStatus;
import com.adn.dabaeum.authorization.application.AuthorizationPolicy;
import com.adn.dabaeum.common.api.ApiErrorCode;
import com.adn.dabaeum.common.api.ApiException;
import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import com.adn.dabaeum.course.domain.Course;
import com.adn.dabaeum.course.domain.CourseInstructorRepository;
import com.adn.dabaeum.course.domain.CourseInstructorRole;
import com.adn.dabaeum.course.domain.CourseRepository;
import com.adn.dabaeum.course.domain.CourseSession;
import com.adn.dabaeum.course.domain.CourseSessionRepository;
import com.adn.dabaeum.enrollment.domain.Enrollment;
import com.adn.dabaeum.enrollment.domain.EnrollmentRepository;
import com.adn.dabaeum.enrollment.domain.EnrollmentStatus;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DefaultAttendanceApplicationService implements AttendanceApplicationService {

    private final AttendanceRepository attendanceRepository;
    private final CourseSessionRepository sessionRepository;
    private final CourseRepository courseRepository;
    private final CourseInstructorRepository instructorRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final AttendanceQrTokenApplicationService qrTokenService;
    private final AuthorizationPolicy authorizationPolicy;
    private final Clock clock;
    private final AttendanceIdGenerator idGenerator;
    private final AttendanceAdjustmentRepository adjustmentRepository;
    private final AttendanceMetricsQuery metricsQuery;

    public DefaultAttendanceApplicationService(
        AttendanceRepository attendanceRepository,
        CourseSessionRepository sessionRepository,
        CourseRepository courseRepository,
        CourseInstructorRepository instructorRepository,
        EnrollmentRepository enrollmentRepository,
        AttendanceQrTokenApplicationService qrTokenService,
        AuthorizationPolicy authorizationPolicy,
        Clock clock,
        AttendanceIdGenerator idGenerator
    ) {
        this(attendanceRepository, sessionRepository, courseRepository, instructorRepository,
            enrollmentRepository, qrTokenService, authorizationPolicy, clock, idGenerator,
            null, null);
    }

    @Autowired
    public DefaultAttendanceApplicationService(
        AttendanceRepository attendanceRepository,
        CourseSessionRepository sessionRepository,
        CourseRepository courseRepository,
        CourseInstructorRepository instructorRepository,
        EnrollmentRepository enrollmentRepository,
        AttendanceQrTokenApplicationService qrTokenService,
        AuthorizationPolicy authorizationPolicy,
        Clock clock,
        AttendanceIdGenerator idGenerator,
        AttendanceAdjustmentRepository adjustmentRepository,
        AttendanceMetricsQuery metricsQuery
    ) {
        this.attendanceRepository = Objects.requireNonNull(attendanceRepository);
        this.sessionRepository = Objects.requireNonNull(sessionRepository);
        this.courseRepository = Objects.requireNonNull(courseRepository);
        this.instructorRepository = Objects.requireNonNull(instructorRepository);
        this.enrollmentRepository = Objects.requireNonNull(enrollmentRepository);
        this.qrTokenService = Objects.requireNonNull(qrTokenService);
        this.authorizationPolicy = Objects.requireNonNull(authorizationPolicy);
        this.clock = Objects.requireNonNull(clock);
        this.idGenerator = Objects.requireNonNull(idGenerator);
        this.adjustmentRepository = adjustmentRepository;
        this.metricsQuery = metricsQuery;
    }

    @Override
    @Transactional
    public Attendance record(
        RecordAttendanceCommand command,
        AuthenticatedUserContext context
    ) {
        validateCommand(command);
        if (command.attendanceMethod() == AttendanceMethod.EXTERNAL) {
            throw new ApiException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                ApiErrorCode.ATTENDANCE_METHOD_NOT_SUPPORTED,
                "External attendance is not supported"
            );
        }

        CourseSession session = findSession(command.sessionId());
        Course course = findCourse(session.courseId());
        Enrollment enrollment = findEnrollment(command.enrollmentId());
        requireApprovedSameCourse(session, enrollment);
        Instant now = clock.instant();

        UUID qrTokenId = null;
        Instant checkedAt = command.checkedAt();
        if (command.attendanceMethod() == AttendanceMethod.QR) {
            if (!Objects.equals(context == null ? null : context.userId(), enrollment.userId())) {
                throw forbidden();
            }
            ValidatedAttendanceQrToken validated = qrTokenService.validate(
                command.qrToken(), session.id());
            if (!session.id().equals(validated.sessionId())) {
                throw qrInvalid();
            }
            checkedAt = now;
            qrTokenId = validated.tokenId();
        } else {
            requireManager(context, course);
        }

        rejectDuplicate(command.sessionId(), command.enrollmentId());
        Attendance attendance = new Attendance(
            Objects.requireNonNull(idGenerator.generate(), "generated id"),
            course.id(),
            session.id(),
            enrollment.id(),
            qrTokenId,
            command.attendanceMethod(),
            command.status(),
            checkedAt == null ? now : checkedAt,
            command.source(),
            context == null ? null : context.userId(),
            now,
            now
        );
        try {
            attendanceRepository.save(attendance);
        } catch (DataIntegrityViolationException exception) {
            throw attendanceConflict();
        }
        return attendance;
    }

    @Override
    @Transactional(readOnly = true)
    public AttendancePage list(
        ListSessionAttendanceQuery query,
        AuthenticatedUserContext context
    ) {
        if (query == null || query.sessionId() == null) {
            throw badRequest("sessionId");
        }
        if (query.page() < 0 || query.size() < 1 || query.size() > 100) {
            throw badRequest("page");
        }
        String[] sort = splitSort(query.sort());
        final AttendanceSort sortValue;
        final AttendanceSortDirection direction;
        try {
            sortValue = AttendanceSort.fromApiValue(sort[0]);
            direction = AttendanceSortDirection.fromApiValue(sort[1]);
        } catch (IllegalArgumentException exception) {
            throw badRequest("sort");
        }
        int offset;
        try {
            offset = Math.multiplyExact(query.page(), query.size());
        } catch (ArithmeticException exception) {
            throw badRequest("page");
        }

        CourseSession session = findSession(query.sessionId());
        Course course = findCourse(session.courseId());
        requireReader(context, course);
        AttendancePageCriteria criteria = new AttendancePageCriteria(
            query.sessionId(), offset, query.size(), sortValue, direction);
        List<Attendance> data = attendanceRepository.findBySession(query.sessionId(), criteria);
        long total = attendanceRepository.countBySession(query.sessionId());
        int totalPages = total == 0 ? 0 : Math.toIntExact(((total - 1) / query.size()) + 1);
        return new AttendancePage(data, query.page(), query.size(), total, totalPages);
    }

    @Override
    @Transactional(readOnly = true)
    public Attendance get(UUID attendanceId, AuthenticatedUserContext context) {
        if (attendanceId == null) {
            throw validation("attendanceId");
        }
        Attendance attendance = attendanceRepository.findById(attendanceId)
            .orElseThrow(this::attendanceNotFound);
        CourseSession session = findSession(attendance.sessionId());
        Course course = findCourse(session.courseId());
        Enrollment enrollment = findEnrollment(attendance.enrollmentId());
        boolean instructorAssigned = isAssignedInstructor(context, course, false);
        authorizationPolicy.requireEnrollmentSubjectOrReader(
            context, enrollment.userId(), course.institutionId(), instructorAssigned);
        return attendance;
    }

    @Override
    @Transactional
    public Attendance adjust(
        AdjustAttendanceCommand command,
        AuthenticatedUserContext context
    ) {
        if (adjustmentRepository == null) {
            throw new IllegalStateException("Attendance adjustment repository is unavailable");
        }
        validateAdjustment(command);
        Attendance current = attendanceRepository.findByIdForUpdate(command.attendanceId())
            .orElseThrow(this::attendanceNotFound);
        CourseSession session = findSession(current.sessionId());
        Course course = findCourse(session.courseId());
        requireManager(context, course);
        if (current.status() == command.status()) {
            throw validation("status");
        }
        Instant now = clock.instant();
        AttendanceAdjustment adjustment = new AttendanceAdjustment(
            UUID.randomUUID(), current.id(), current.status(), command.status(),
            command.reason().trim(), context.userId(), now, now);
        adjustmentRepository.save(adjustment);
        if (!attendanceRepository.updateStatus(
            current.id(), current.status(), command.status(), now)) {
            throw attendanceConflict();
        }
        return attendanceRepository.findById(current.id()).orElseThrow(this::attendanceNotFound);
    }

    @Override
    @Transactional(readOnly = true)
    public AttendanceMetrics summary(
        UUID enrollmentId,
        AuthenticatedUserContext context
    ) {
        if (metricsQuery == null) {
            throw new IllegalStateException("Attendance metrics query is unavailable");
        }
        if (enrollmentId == null) {
            throw validation("enrollmentId");
        }
        Enrollment enrollment = findEnrollment(enrollmentId);
        Course course = findCourse(enrollment.courseId());
        boolean instructorAssigned = isAssignedInstructor(context, course, false);
        authorizationPolicy.requireEnrollmentSubjectOrReader(
            context, enrollment.userId(), course.institutionId(), instructorAssigned);
        return metricsQuery.calculate(enrollmentId, clock.instant());
    }

    private void validateCommand(RecordAttendanceCommand command) {
        if (command == null || command.sessionId() == null || command.enrollmentId() == null
            || command.attendanceMethod() == null || command.status() == null
            || command.source() == null) {
            throw validation("requestBody");
        }
        if (command.attendanceMethod() == AttendanceMethod.QR) {
            if (command.qrToken() == null || command.qrToken().isBlank()
                || command.source() != AttendanceSource.APP
                || command.status() != AttendanceStatus.PRESENT
                || command.checkedAt() != null) {
                throw validation("requestBody");
            }
        } else if (command.attendanceMethod() == AttendanceMethod.ADMIN) {
            if (command.qrToken() != null || command.source() != AttendanceSource.ADMIN_WEB) {
                throw validation("requestBody");
            }
        } else if (command.qrToken() != null || command.source() != AttendanceSource.EXTERNAL_API) {
            throw validation("requestBody");
        }
    }

    private void validateAdjustment(AdjustAttendanceCommand command) {
        if (command == null || command.attendanceId() == null || command.status() == null) {
            throw validation("requestBody");
        }
        if (command.reason() == null || command.reason().isBlank()
            || command.reason().length() > 1000) {
            throw validation("reason");
        }
    }

    private void requireApprovedSameCourse(CourseSession session, Enrollment enrollment) {
        if (!session.courseId().equals(enrollment.courseId())) {
            throw new ApiException(
                HttpStatus.FORBIDDEN, ApiErrorCode.INSTITUTION_SCOPE_FORBIDDEN,
                "Enrollment is outside the session course");
        }
        if (enrollment.status() != EnrollmentStatus.APPROVED) {
            throw new ApiException(
                HttpStatus.CONFLICT, ApiErrorCode.ENROLLMENT_STATUS_CONFLICT,
                "Enrollment is not approved");
        }
    }

    private void rejectDuplicate(UUID sessionId, UUID enrollmentId) {
        if (attendanceRepository.findBySessionAndEnrollment(sessionId, enrollmentId).isPresent()) {
            throw attendanceConflict();
        }
    }

    private void requireManager(AuthenticatedUserContext context, Course course) {
        boolean mainAssigned = isAssignedInstructor(context, course, true);
        authorizationPolicy.requireCourseSessionManager(
            context, course.institutionId(), mainAssigned);
    }

    private void requireReader(AuthenticatedUserContext context, Course course) {
        boolean assigned = isAssignedInstructor(context, course, false);
        authorizationPolicy.requireEnrollmentReader(
            context, course.institutionId(), assigned);
    }

    private boolean isAssignedInstructor(
        AuthenticatedUserContext context,
        Course course,
        boolean mainOnly
    ) {
        if (context == null || context.roles().stream().noneMatch(role ->
            "INSTRUCTOR".equals(role.role())
                && Objects.equals(role.institutionId(), course.institutionId()))) {
            return false;
        }
        return mainOnly
            ? instructorRepository.existsByCourseIdAndUserIdAndRole(
                course.id(), context.userId(), CourseInstructorRole.MAIN)
            : instructorRepository.existsByCourseIdAndUserId(course.id(), context.userId());
    }

    private CourseSession findSession(UUID id) {
        return sessionRepository.findById(id).orElseThrow(this::sessionNotFound);
    }

    private Course findCourse(UUID id) {
        return courseRepository.findActiveById(id).orElseThrow(this::courseNotFound);
    }

    private Enrollment findEnrollment(UUID id) {
        return enrollmentRepository.findById(id).orElseThrow(this::enrollmentNotFound);
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

    private ApiException validation(String field) {
        return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
            ApiErrorCode.VALIDATION_FAILED, "Request validation failed", List.of(field));
    }

    private ApiException badRequest(String field) {
        return new ApiException(HttpStatus.BAD_REQUEST,
            ApiErrorCode.BAD_REQUEST, "Request parameter is invalid", List.of(field));
    }

    private ApiException forbidden() {
        return new ApiException(HttpStatus.FORBIDDEN, ApiErrorCode.FORBIDDEN,
            "Attendance subject scope is not allowed");
    }

    private ApiException qrInvalid() {
        return new ApiException(HttpStatus.CONFLICT, ApiErrorCode.ATTENDANCE_QR_INVALID,
            "Attendance QR token is invalid");
    }

    private ApiException attendanceConflict() {
        return new ApiException(HttpStatus.CONFLICT, ApiErrorCode.ATTENDANCE_CONFLICT,
            "Attendance already exists");
    }

    private ApiException attendanceNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, ApiErrorCode.ATTENDANCE_NOT_FOUND,
            "Attendance not found");
    }

    private ApiException sessionNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, ApiErrorCode.COURSE_SESSION_NOT_FOUND,
            "Course session not found");
    }

    private ApiException courseNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, ApiErrorCode.COURSE_NOT_FOUND,
            "Course not found");
    }

    private ApiException enrollmentNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, ApiErrorCode.ENROLLMENT_NOT_FOUND,
            "Enrollment not found");
    }
}
