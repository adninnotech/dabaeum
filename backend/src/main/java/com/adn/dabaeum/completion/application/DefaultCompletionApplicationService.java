package com.adn.dabaeum.completion.application;

import com.adn.dabaeum.attendance.application.AttendanceMetrics;
import com.adn.dabaeum.attendance.application.AttendanceMetricsQuery;
import com.adn.dabaeum.authorization.application.AuthorizationPolicy;
import com.adn.dabaeum.common.api.ApiErrorCode;
import com.adn.dabaeum.common.api.ApiException;
import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import com.adn.dabaeum.completion.domain.Completion;
import com.adn.dabaeum.completion.domain.CompletionConfirmedEvent;
import com.adn.dabaeum.completion.domain.CompletionOutboxRepository;
import com.adn.dabaeum.completion.domain.CompletionRepository;
import com.adn.dabaeum.completion.domain.CompletionStatus;
import com.adn.dabaeum.completion.domain.CompletionStatusPolicy;
import com.adn.dabaeum.course.domain.Course;
import com.adn.dabaeum.course.domain.CourseInstructorRepository;
import com.adn.dabaeum.course.domain.CourseInstructorRole;
import com.adn.dabaeum.course.domain.CourseRepository;
import com.adn.dabaeum.course.domain.CourseSessionRepository;
import com.adn.dabaeum.enrollment.domain.Enrollment;
import com.adn.dabaeum.enrollment.domain.EnrollmentRepository;
import com.adn.dabaeum.enrollment.domain.EnrollmentStatus;
import java.math.BigDecimal;
import com.adn.dabaeum.correction.application.CorrectionReasons;
import com.adn.dabaeum.correction.domain.AdminCorrection;
import com.adn.dabaeum.correction.domain.AdminCorrectionRepository;
import com.adn.dabaeum.correction.domain.CorrectionTargetType;
import com.adn.dabaeum.credential.domain.CredentialGroupRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DefaultCompletionApplicationService implements CompletionApplicationService {

    private final CompletionRepository completionRepository;
    private final CompletionOutboxRepository outboxRepository;
    private final CompletionIdGenerator idGenerator;
    private final EnrollmentRepository enrollmentRepository;
    private final CourseRepository courseRepository;
    private final CourseInstructorRepository instructorRepository;
    private final CourseSessionRepository sessionRepository;
    private final AttendanceMetricsQuery metricsQuery;
    private final AuthorizationPolicy authorizationPolicy;
    private final CompletionStatusPolicy statusPolicy = new CompletionStatusPolicy();
    private final CredentialGroupRepository credentialGroupRepository;
    private final AdminCorrectionRepository corrections;
    private final Clock clock;

    public DefaultCompletionApplicationService(
        CompletionRepository completionRepository,
        CompletionOutboxRepository outboxRepository,
        CompletionIdGenerator idGenerator,
        EnrollmentRepository enrollmentRepository,
        CourseRepository courseRepository,
        CourseInstructorRepository instructorRepository,
        CourseSessionRepository sessionRepository,
        AttendanceMetricsQuery metricsQuery,
        AuthorizationPolicy authorizationPolicy,
        CredentialGroupRepository credentialGroupRepository,
        AdminCorrectionRepository corrections,
        Clock clock
    ) {
        this.credentialGroupRepository = Objects.requireNonNull(
            credentialGroupRepository, "credentialGroupRepository");
        this.corrections = Objects.requireNonNull(corrections, "corrections");
        this.completionRepository = Objects.requireNonNull(completionRepository);
        this.outboxRepository = Objects.requireNonNull(outboxRepository);
        this.idGenerator = Objects.requireNonNull(idGenerator);
        this.enrollmentRepository = Objects.requireNonNull(enrollmentRepository);
        this.courseRepository = Objects.requireNonNull(courseRepository);
        this.instructorRepository = Objects.requireNonNull(instructorRepository);
        this.sessionRepository = Objects.requireNonNull(sessionRepository);
        this.metricsQuery = Objects.requireNonNull(metricsQuery);
        this.authorizationPolicy = Objects.requireNonNull(authorizationPolicy);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    @Transactional(readOnly = true)
    public Completion get(UUID enrollmentId, AuthenticatedUserContext context) {
        if (enrollmentId == null) {
            throw validation("enrollmentId");
        }
        Enrollment enrollment = findEnrollment(enrollmentId);
        Course course = findCourse(enrollment.courseId());
        boolean assigned = isAssignedInstructor(context, course, false);
        authorizationPolicy.requireEnrollmentSubjectOrReader(
            context, enrollment.userId(), course.institutionId(), assigned);
        return completionRepository.findByEnrollmentId(enrollmentId)
            .orElseThrow(this::completionNotFound);
    }

    @Override
    @Transactional
    public Completion evaluate(
        EvaluateCompletionCommand command,
        AuthenticatedUserContext context
    ) {
        validateCommand(command);
        Enrollment enrollment = findEnrollment(command.enrollmentId());
        Course course = findCourse(enrollment.courseId());
        requireApproved(enrollment);
        requireDecisionManager(context, course);
        Instant now = clock.instant();
        if (sessionRepository.existsAttendanceIneligibleByCourseId(course.id(), now)) {
            throw conflict(ApiErrorCode.COMPLETION_STATUS_CONFLICT,
                "Attendance sessions are not closed");
        }
        AttendanceMetrics metrics = metricsQuery.calculate(command.enrollmentId(), now);
        // 판정에는 항상 서버 계산값을 쓴다. 호출자가 보낸 값은 참고용이라 불일치해도 거부하지 않는다.
        command = withServerMetrics(command, metrics, course);
        String reason = normalizeReason(command.failureReason());
        CompletionStatus nextStatus = reason == null
            ? CompletionStatus.ELIGIBLE : CompletionStatus.NOT_COMPLETED;

        Optional<Completion> current = completionRepository.findByEnrollmentId(
            command.enrollmentId());
        if (current.isPresent()) {
            return updateEvaluation(current.get(), nextStatus, command, reason, now);
        }

        Enrollment lockedEnrollment = enrollmentRepository.findByIdForUpdate(
            command.enrollmentId()).orElseThrow(this::enrollmentNotFound);
        requireApproved(lockedEnrollment);
        current = completionRepository.findByEnrollmentIdForUpdate(command.enrollmentId());
        if (current.isPresent()) {
            return updateEvaluation(current.get(), nextStatus, command, reason, now);
        }

        Completion created = new Completion(
            Objects.requireNonNull(idGenerator.generate(), "generated completion id"),
            command.enrollmentId(), nextStatus, command.attendanceRate(),
            command.completedMinutes(), command.creditValue(), now, null, null, null, reason,
            now, now);
        try {
            completionRepository.save(created);
        } catch (DataIntegrityViolationException exception) {
            throw conflict(ApiErrorCode.COMPLETION_STATUS_CONFLICT,
                "Completion state changed concurrently");
        }
        return created;
    }

    @Override
    @Transactional
    public Completion confirm(UUID enrollmentId, AuthenticatedUserContext context) {
        if (enrollmentId == null) {
            throw validation("enrollmentId");
        }
        Enrollment enrollment = findEnrollment(enrollmentId);
        Course course = findCourse(enrollment.courseId());
        requireApproved(enrollment);
        requireDecisionManager(context, course);
        Completion current = completionRepository.findByEnrollmentIdForUpdate(enrollmentId)
            .orElseThrow(this::completionNotFound);
        if (!statusPolicy.canConfirm(current.status())) {
            throw conflict(ApiErrorCode.COMPLETION_STATUS_CONFLICT,
                "Completion cannot be confirmed in its current state");
        }
        Instant now = clock.instant();
        Completion completed = new Completion(
            current.id(), current.enrollmentId(), CompletionStatus.COMPLETED,
            current.attendanceRate(), current.completedMinutes(), current.creditValue(),
            current.evaluatedAt(), now, context.userId(), now, null,
            current.createdAt(), now);
        if (!completionRepository.confirm(completed, CompletionStatus.ELIGIBLE)) {
            throw conflict(ApiErrorCode.COMPLETION_STATUS_CONFLICT,
                "Completion state changed concurrently");
        }
        outboxRepository.save(new CompletionConfirmedEvent(
            UUID.randomUUID(), completed.id(), completed.enrollmentId(), course.id(), now));
        return completed;
    }

    private Completion updateEvaluation(
        Completion current,
        CompletionStatus nextStatus,
        EvaluateCompletionCommand command,
        String reason,
        Instant now
    ) {
        if (!statusPolicy.canEvaluate(current.status())) {
            throw conflict(ApiErrorCode.COMPLETION_STATUS_CONFLICT,
                "Completion cannot be evaluated in its current state");
        }
        Completion updated = new Completion(
            current.id(), current.enrollmentId(), nextStatus, command.attendanceRate(),
            command.completedMinutes(), command.creditValue(), now, null, null, null, reason,
            current.createdAt(), now);
        if (!completionRepository.updateEvaluation(updated, current.status())) {
            throw conflict(ApiErrorCode.COMPLETION_STATUS_CONFLICT,
                "Completion state changed concurrently");
        }
        return updated;
    }

    private void validateCommand(EvaluateCompletionCommand command) {
        if (command == null || command.enrollmentId() == null) {
            throw validation("requestBody");
        }
        if (command.completedMinutes() != null && command.completedMinutes() < 0) {
            throw validation("completedMinutes");
        }
        if (command.attendanceRate() != null
            && (command.attendanceRate().compareTo(BigDecimal.ZERO) < 0
                || command.attendanceRate().compareTo(BigDecimal.valueOf(100)) > 0)) {
            throw validation("attendanceRate");
        }
        if (command.creditValue() != null
            && (command.creditValue().compareTo(new BigDecimal("0.01")) < 0
                || command.creditValue().compareTo(new BigDecimal("999.99")) > 0)) {
            throw validation("creditValue");
        }
        if (command.failureReason() != null
            && command.failureReason().trim().length() > 1000) {
            throw validation("failureReason");
        }
    }

    @Override
    @Transactional
    public Completion revertConfirmation(
        UUID enrollmentId, String reason, AuthenticatedUserContext context
    ) {
        if (enrollmentId == null) {
            throw validation("enrollmentId");
        }
        String correctionReason = CorrectionReasons.require(reason);
        Enrollment enrollment = findEnrollment(enrollmentId);
        findCourse(enrollment.courseId());
        // 정정은 되돌릴 수 없는 전이를 뒤집는 조작이라 플랫폼 관리자만 허용한다.
        authorizationPolicy.requirePlatformAdmin(context);

        Completion current = completionRepository.findByEnrollmentIdForUpdate(enrollmentId)
            .orElseThrow(this::completionNotFound);
        if (!statusPolicy.canRevert(current.status())) {
            throw conflict(ApiErrorCode.CORRECTION_NOT_ALLOWED, "Completion is not confirmed");
        }
        // 수료증이 발급됐으면 되돌리지 않는다. 원장 기록은 지울 수 없어 DB 와 어긋난다.
        if (credentialGroupRepository.findByCompletionId(current.id()).isPresent()) {
            throw conflict(ApiErrorCode.CORRECTION_NOT_ALLOWED,
                "Completion has an issued credential and cannot be reverted");
        }

        Instant now = clock.instant();
        Completion reverted = new Completion(
            current.id(), current.enrollmentId(), CompletionStatus.ELIGIBLE,
            current.attendanceRate(), current.completedMinutes(), current.creditValue(),
            current.evaluatedAt(), null, null, null, null, current.createdAt(), now);
        if (!completionRepository.revertConfirmation(reverted, CompletionStatus.COMPLETED)) {
            throw conflict(ApiErrorCode.COMPLETION_STATUS_CONFLICT,
                "Completion state changed concurrently");
        }
        corrections.insert(new AdminCorrection(
            UUID.randomUUID(), CorrectionTargetType.COMPLETION, current.id(),
            CompletionStatus.COMPLETED.name(), CompletionStatus.ELIGIBLE.name(),
            correctionReason, context.userId(), now));
        return reverted;
    }

    private EvaluateCompletionCommand withServerMetrics(
        EvaluateCompletionCommand command,
        AttendanceMetrics metrics,
        Course course
    ) {
        BigDecimal credit = course.creditBankEligible() ? course.creditValue() : null;
        return new EvaluateCompletionCommand(
            command.enrollmentId(), metrics.attendanceRate(), metrics.completedMinutes(),
            credit, command.failureReason());
    }

    private void requireDecisionManager(
        AuthenticatedUserContext context,
        Course course
    ) {
        boolean mainAssigned = isAssignedInstructor(context, course, true);
        authorizationPolicy.requireEnrollmentDecisionManager(
            context, course.institutionId(), mainAssigned);
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

    private Enrollment findEnrollment(UUID enrollmentId) {
        return enrollmentRepository.findById(enrollmentId)
            .orElseThrow(this::enrollmentNotFound);
    }

    private Course findCourse(UUID courseId) {
        return courseRepository.findActiveById(courseId).orElseThrow(this::courseNotFound);
    }

    private void requireApproved(Enrollment enrollment) {
        if (enrollment.status() != EnrollmentStatus.APPROVED) {
            throw conflict(ApiErrorCode.ENROLLMENT_STATUS_CONFLICT,
                "Enrollment is not approved");
        }
    }

    private String normalizeReason(String reason) {
        if (reason == null) {
            return null;
        }
        String normalized = reason.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private ApiException validation(String field) {
        return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
            ApiErrorCode.VALIDATION_FAILED, "Request validation failed", List.of(field));
    }

    private ApiException conflict(ApiErrorCode code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message);
    }

    private ApiException enrollmentNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, ApiErrorCode.ENROLLMENT_NOT_FOUND,
            "Enrollment not found");
    }

    private ApiException courseNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, ApiErrorCode.COURSE_NOT_FOUND,
            "Course not found");
    }

    private ApiException completionNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, ApiErrorCode.COMPLETION_NOT_FOUND,
            "Completion not found");
    }
}
