package com.adn.dabaeum.attendance.application;

import com.adn.dabaeum.attendance.domain.Attendance;
import com.adn.dabaeum.attendance.domain.AttendanceLearner;
import com.adn.dabaeum.attendance.domain.AttendanceAdjustment;
import com.adn.dabaeum.attendance.domain.AttendanceAdjustmentEntry;
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
import com.adn.dabaeum.common.api.SortParameter;
import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import com.adn.dabaeum.course.application.CourseInstructorAssignments;
import com.adn.dabaeum.course.domain.Course;
import com.adn.dabaeum.course.domain.CourseInstructorRepository;
import com.adn.dabaeum.course.domain.CourseRepository;
import com.adn.dabaeum.course.domain.CourseSession;
import com.adn.dabaeum.course.domain.CourseSessionRepository;
import com.adn.dabaeum.enrollment.domain.Enrollment;
import com.adn.dabaeum.enrollment.domain.EnrollmentRepository;
import com.adn.dabaeum.enrollment.domain.EnrollmentStatus;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
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
        AuthorizationPolicy authorizationPolicy,
        Clock clock,
        AttendanceIdGenerator idGenerator
    ) {
        this(attendanceRepository, sessionRepository, courseRepository, instructorRepository,
            enrollmentRepository, authorizationPolicy, clock,
            idGenerator, null, null);
    }

    @Autowired
    public DefaultAttendanceApplicationService(
        AttendanceRepository attendanceRepository,
        CourseSessionRepository sessionRepository,
        CourseRepository courseRepository,
        CourseInstructorRepository instructorRepository,
        EnrollmentRepository enrollmentRepository,
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
        // 다대구 출결은 로그인 없이 DID로 본인을 증명하는 별도 경로만 만든다.
        if (command != null && command.attendanceMethod() == AttendanceMethod.QR_DADAEGU) {
            throw new ApiException(
                HttpStatus.UNPROCESSABLE_CONTENT,
                ApiErrorCode.ATTENDANCE_METHOD_NOT_SUPPORTED,
                "Dadaegu attendance must use /sessions/{sessionId}/attendance/dadaegu"
            );
        }
        // 일반 QR 은 닫았다. attendUrl 의 토큰만 있으면 현장에 없어도 본인 계정으로 찍혔다.
        // 다대구 회원은 /attend(QR_DADAEGU), 그 밖은 강사·관리자가 ADMIN 으로 기록한다.
        if (command != null && command.attendanceMethod() == AttendanceMethod.QR) {
            throw new ApiException(
                HttpStatus.UNPROCESSABLE_CONTENT,
                ApiErrorCode.ATTENDANCE_METHOD_NOT_SUPPORTED,
                "QR attendance is closed; use the Dadaegu attend flow or instructor recording"
            );
        }
        validateCommand(command);
        if (command.attendanceMethod() == AttendanceMethod.EXTERNAL) {
            throw new ApiException(
                HttpStatus.UNPROCESSABLE_CONTENT,
                ApiErrorCode.ATTENDANCE_METHOD_NOT_SUPPORTED,
                "External attendance is not supported"
            );
        }

        CourseSession session = findSession(command.sessionId());
        Course course = findCourse(session.courseId());
        Enrollment enrollment = findEnrollment(command.enrollmentId());
        requireApprovedSameCourse(session, enrollment);
        Instant now = clock.instant();

        requireManager(context, course);

        rejectDuplicate(command.sessionId(), command.enrollmentId());
        Instant checkedAt = command.checkedAt();
        return insert(session, enrollment, null, command.attendanceMethod(),
            command.status(), command.source(), checkedAt == null ? now : checkedAt,
            context == null ? null : context.userId(), now);
    }

    /**
     * 학생 폰이 QR(URL)을 찍어 다대구 앱 인증을 마치면 서버가 대신 부른다. 로그인 컨텍스트가 없으므로
     * "그 학생이 맞다"는 다대구 DID 가, "그 강의실에 있었다"는 강사 QR 토큰이 증명한다.
     *
     * <p>토큰은 학생이 찍는 순간 이미 검증됐고 그 결과를 받아 온다. 여기서 다시 검증하지 않는다 —
     * 그 사이 토큰은 서버 메모리에만 있어 바뀔 수 없고, 다시 보면 인증에 시간을 끈 학생이
     * 만료로 거부된다(TTL 30초 < 폴링 90초).
     *
     * <p>createdBy 는 토큰 발급자(강사). 학생은 로그인하지 않아 남길 주체가 이것뿐이다.
     */
    @Override
    @Transactional
    public Attendance recordDadaegu(RecordDadaeguAttendanceCommand command) {
        if (command == null || command.userId() == null || command.qrToken() == null) {
            throw validation("requestBody");
        }
        ValidatedAttendanceQrToken token = command.qrToken();
        CourseSession session = findSession(token.sessionId());
        Course course = findCourse(session.courseId());
        // 학생은 로그인하지 않으므로 DID 로 확인된 사용자의 활성 수강신청을 서버가 찾는다.
        // (course_id, user_id) 활성 부분 유니크 인덱스가 있어 결과는 최대 1건이다.
        Enrollment enrollment = enrollmentRepository
            .findActiveByCourseIdAndUserId(course.id(), command.userId())
            .orElseThrow(this::enrollmentNotFound);
        requireApprovedSameCourse(session, enrollment);
        rejectDuplicate(session.id(), enrollment.id());
        Instant now = clock.instant();
        return insert(session, enrollment, token.tokenId(), AttendanceMethod.QR_DADAEGU,
            AttendanceStatus.PRESENT, AttendanceSource.APP, now, token.issuedBy(), now);
    }

    private Attendance insert(
        CourseSession session,
        Enrollment enrollment,
        UUID qrTokenId,
        AttendanceMethod method,
        AttendanceStatus status,
        AttendanceSource source,
        Instant checkedAt,
        UUID createdBy,
        Instant now
    ) {
        Attendance attendance = new Attendance(
            Objects.requireNonNull(idGenerator.generate(), "generated id"),
            session.courseId(), session.id(), enrollment.id(), qrTokenId,
            method, status, checkedAt, source, createdBy, now, now);
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
        Map<UUID, AttendanceLearner> learners = attendanceRepository.findLearners(
                data.stream().map(Attendance::enrollmentId).distinct().toList())
            .stream()
            .collect(Collectors.toMap(AttendanceLearner::enrollmentId, Function.identity()));
        return new AttendancePage(data, query.page(), query.size(), total, totalPages, learners);
    }

    @Override
    @Transactional(readOnly = true)
    public AttendanceDetail get(UUID attendanceId, AuthenticatedUserContext context) {
        if (attendanceId == null) {
            throw validation("attendanceId");
        }
        Attendance attendance = attendanceRepository.findById(attendanceId)
            .orElseThrow(this::attendanceNotFound);
        CourseSession session = findSession(attendance.sessionId());
        Course course = findCourse(session.courseId());
        requireSubjectOrReader(context, findEnrollment(attendance.enrollmentId()), course);
        List<AttendanceAdjustmentEntry> adjustments = adjustmentRepository == null
            ? List.of()
            : adjustmentRepository.findEntriesByAttendanceId(attendanceId);
        return new AttendanceDetail(attendance, adjustments);
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
        requireSubjectOrReader(context, enrollment, findCourse(enrollment.courseId()));
        return metricsQuery.calculate(enrollmentId, clock.instant());
    }

    private void validateCommand(RecordAttendanceCommand command) {
        if (command == null || command.sessionId() == null || command.enrollmentId() == null
            || command.attendanceMethod() == null || command.status() == null
            || command.source() == null) {
            throw validation("requestBody");
        }
        if (command.attendanceMethod() == AttendanceMethod.ADMIN) {
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

    private void requireSubjectOrReader(
        AuthenticatedUserContext context,
        Enrollment enrollment,
        Course course
    ) {
        boolean assigned = isAssignedInstructor(context, course, false);
        authorizationPolicy.requireEnrollmentSubjectOrReader(
            context, enrollment.userId(), course.institutionId(), assigned);
    }

    private boolean isAssignedInstructor(
        AuthenticatedUserContext context,
        Course course,
        boolean mainOnly
    ) {
        return CourseInstructorAssignments.isAssigned(
            instructorRepository, context, course, mainOnly);
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
        return SortParameter.split(value).orElseThrow(() -> badRequest("sort"));
    }

    private ApiException validation(String field) {
        return new ApiException(HttpStatus.UNPROCESSABLE_CONTENT,
            ApiErrorCode.VALIDATION_FAILED, "Request validation failed", List.of(field));
    }

    private ApiException badRequest(String field) {
        return new ApiException(HttpStatus.BAD_REQUEST,
            ApiErrorCode.BAD_REQUEST, "Request parameter is invalid", List.of(field));
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
