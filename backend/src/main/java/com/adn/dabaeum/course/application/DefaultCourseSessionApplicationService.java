package com.adn.dabaeum.course.application;

import com.adn.dabaeum.authorization.application.AuthorizationPolicy;
import com.adn.dabaeum.common.api.ApiErrorCode;
import com.adn.dabaeum.common.api.ApiException;
import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import com.adn.dabaeum.course.domain.Course;
import com.adn.dabaeum.course.domain.CourseInstructorRepository;
import com.adn.dabaeum.course.domain.CourseInstructorRole;
import com.adn.dabaeum.course.domain.CourseRepository;
import com.adn.dabaeum.course.domain.CourseSession;
import com.adn.dabaeum.course.domain.CourseSessionPageCriteria;
import com.adn.dabaeum.course.domain.CourseSessionRepository;
import com.adn.dabaeum.course.domain.CourseSessionSort;
import com.adn.dabaeum.course.domain.CourseSessionSortDirection;
import com.adn.dabaeum.course.domain.CourseSessionStatus;
import com.adn.dabaeum.course.domain.CourseSessionStatusPolicy;
import com.adn.dabaeum.correction.application.CorrectionReasons;
import com.adn.dabaeum.correction.domain.AdminCorrection;
import com.adn.dabaeum.correction.domain.AdminCorrectionRepository;
import com.adn.dabaeum.correction.domain.CorrectionTargetType;
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
public class DefaultCourseSessionApplicationService
    implements CourseSessionApplicationService {

    private final CourseSessionRepository sessionRepository;
    private final CourseRepository courseRepository;
    private final CourseInstructorRepository instructorRepository;
    private final CourseSessionIdGenerator idGenerator;
    private final AuthorizationPolicy authorizationPolicy;
    private final AdminCorrectionRepository corrections;
    private final Clock clock;

    public DefaultCourseSessionApplicationService(
        CourseSessionRepository sessionRepository,
        CourseRepository courseRepository,
        CourseInstructorRepository instructorRepository,
        CourseSessionIdGenerator idGenerator,
        AuthorizationPolicy authorizationPolicy,
        AdminCorrectionRepository corrections,
        Clock clock
    ) {
        this.corrections = Objects.requireNonNull(corrections, "corrections");
        this.sessionRepository = Objects.requireNonNull(sessionRepository, "sessionRepository");
        this.courseRepository = Objects.requireNonNull(courseRepository, "courseRepository");
        this.instructorRepository = Objects.requireNonNull(
            instructorRepository, "instructorRepository");
        this.idGenerator = Objects.requireNonNull(idGenerator, "idGenerator");
        this.authorizationPolicy = Objects.requireNonNull(
            authorizationPolicy, "authorizationPolicy");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    @Transactional(readOnly = true)
    public CourseSessionPage list(ListCourseSessionsQuery query) {
        if (query == null || query.courseId() == null) {
            throw badRequest("query");
        }
        Course course = findCourse(query.courseId());
        if (query.page() < 0 || query.size() < 1 || query.size() > 100) {
            throw badRequest("page");
        }
        String[] sortParts = splitSort(query.sort());
        final CourseSessionSort sort;
        final CourseSessionSortDirection direction;
        try {
            sort = CourseSessionSort.fromApiValue(sortParts[0]);
            direction = CourseSessionSortDirection.fromApiValue(sortParts[1]);
        } catch (IllegalArgumentException exception) {
            throw badRequest("sort");
        }
        final int offset;
        try {
            offset = Math.multiplyExact(query.page(), query.size());
        } catch (ArithmeticException exception) {
            throw badRequest("page");
        }
        List<CourseSession> data = sessionRepository.findPageByCourseId(
            new CourseSessionPageCriteria(query.courseId(), offset, query.size(), sort, direction));
        long total = sessionRepository.countByCourseId(query.courseId());
        int pages = total == 0 ? 0 : Math.toIntExact(((total - 1) / query.size()) + 1);
        return new CourseSessionPage(List.copyOf(data), query.page(), query.size(), total, pages);
    }

    @Override
    @Transactional
    public CourseSession create(
        CreateCourseSessionCommand command,
        AuthenticatedUserContext context
    ) {
        if (command == null || command.courseId() == null || command.sessionNo() == null
            || command.startsAt() == null || command.endsAt() == null
            || command.status() == null) {
            throw validation("requestBody");
        }
        Course course = findCourse(command.courseId());
        requireManager(context, course);
        if (sessionRepository.findByCourseIdAndSessionNo(
            command.courseId(), command.sessionNo()).isPresent()) {
            throw sessionConflict();
        }
        Instant now = clock.instant();
        CourseSession session;
        try {
            session = new CourseSession(
                Objects.requireNonNull(idGenerator.generate(), "generated id"),
                command.courseId(),
                command.sessionNo(),
                command.startsAt(),
                command.endsAt(),
                command.location(),
                command.attendanceOpensAt(),
                command.attendanceClosesAt(),
                command.status(),
                now,
                now
            );
            sessionRepository.save(session);
        } catch (IllegalArgumentException exception) {
            throw validation("requestBody");
        } catch (DataIntegrityViolationException exception) {
            throw sessionConflict();
        }
        return session;
    }

    @Override
    @Transactional(readOnly = true)
    public CourseSession get(UUID sessionId) {
        if (sessionId == null) {
            throw validation("sessionId");
        }
        return sessionRepository.findById(sessionId).orElseThrow(this::sessionNotFound);
    }

    @Override
    @Transactional
    public CourseSession update(
        UpdateCourseSessionCommand command,
        AuthenticatedUserContext context
    ) {
        if (command == null || command.sessionId() == null || command.presentFieldCount() == 0) {
            throw validation("requestBody");
        }
        CourseSession existing = sessionRepository.findById(command.sessionId())
            .orElseThrow(this::sessionNotFound);
        Course course = findCourse(existing.courseId());
        requireManager(context, course);
        if ((existing.status() == CourseSessionStatus.COMPLETED
            || existing.status() == CourseSessionStatus.CANCELLED)) {
            throw sessionConflict();
        }

        CourseSessionUpdateField<Integer> sessionNo = field(command.sessionNo());
        CourseSessionUpdateField<Instant> startsAt = field(command.startsAt());
        CourseSessionUpdateField<Instant> endsAt = field(command.endsAt());
        CourseSessionUpdateField<CourseSessionStatus> status = field(command.status());
        if ((sessionNo.present() && sessionNo.value() == null)
            || (startsAt.present() && startsAt.value() == null)
            || (endsAt.present() && endsAt.value() == null)
            || (status.present() && status.value() == null)) {
            throw validation("requestBody");
        }

        CourseSessionStatus targetStatus = existing.status();
        if (status.present()) {
            try {
                targetStatus = CourseSessionStatusPolicy.updateTarget(
                    existing.status(), status.value());
            } catch (IllegalStateException exception) {
                throw sessionConflict();
            }
        }
        CourseSession updated;
        try {
            updated = new CourseSession(
                existing.id(),
                existing.courseId(),
                valueOrExisting(sessionNo, existing.sessionNo()),
                valueOrExisting(startsAt, existing.startsAt()),
                valueOrExisting(endsAt, existing.endsAt()),
                valueOrExisting(command.location(), existing.location()),
                valueOrExisting(command.attendanceOpensAt(), existing.attendanceOpensAt()),
                valueOrExisting(command.attendanceClosesAt(), existing.attendanceClosesAt()),
                targetStatus,
                existing.createdAt(),
                clock.instant()
            );
            if (!sessionRepository.update(updated)) {
                throw sessionNotFound();
            }
        } catch (IllegalArgumentException exception) {
            throw validation("requestBody");
        } catch (DataIntegrityViolationException exception) {
            throw sessionConflict();
        }
        return updated;
    }

    @Override
    @Transactional
    public CourseSession reopen(UUID sessionId, String reason, AuthenticatedUserContext context) {
        if (sessionId == null) {
            throw validation("sessionId");
        }
        String correctionReason = CorrectionReasons.require(reason);
        CourseSession existing = sessionRepository.findById(sessionId)
            .orElseThrow(this::sessionNotFound);
        // 정정은 되돌릴 수 없는 전이를 뒤집는 조작이라 플랫폼 관리자만 허용한다.
        authorizationPolicy.requirePlatformAdmin(context);

        CourseSessionStatus target;
        try {
            target = CourseSessionStatusPolicy.reopenTarget(existing.status());
        } catch (IllegalStateException exception) {
            throw new ApiException(HttpStatus.CONFLICT, ApiErrorCode.CORRECTION_NOT_ALLOWED,
                "Course session cannot be reopened in its current state");
        }

        Instant now = clock.instant();
        CourseSession updated = new CourseSession(
            existing.id(), existing.courseId(), existing.sessionNo(), existing.startsAt(),
            existing.endsAt(), existing.location(), existing.attendanceOpensAt(),
            existing.attendanceClosesAt(), target, existing.createdAt(), now);
        if (!sessionRepository.update(updated)) {
            throw sessionNotFound();
        }
        corrections.insert(new AdminCorrection(
            UUID.randomUUID(), CorrectionTargetType.COURSE_SESSION, sessionId,
            existing.status().name(), target.name(), correctionReason, context.userId(), now));
        return updated;
    }

    private void requireManager(AuthenticatedUserContext context, Course course) {
        boolean mainAssigned = context != null
            && context.roles().stream().anyMatch(role ->
                "INSTRUCTOR".equals(role.role())
                    && Objects.equals(role.institutionId(), course.institutionId()))
            && instructorRepository.existsByCourseIdAndUserIdAndRole(
                course.id(), context.userId(), CourseInstructorRole.MAIN);
        authorizationPolicy.requireCourseSessionManager(
            context, course.institutionId(), mainAssigned);
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

    private <T> CourseSessionUpdateField<T> field(CourseSessionUpdateField<T> value) {
        return value == null ? CourseSessionUpdateField.absent() : value;
    }

    private <T> T valueOrExisting(CourseSessionUpdateField<T> field, T existing) {
        return field.present() ? field.value() : existing;
    }

    private ApiException validation(String detail) {
        return new ApiException(
            HttpStatus.UNPROCESSABLE_ENTITY,
            ApiErrorCode.VALIDATION_FAILED,
            "Request validation failed",
            List.of(detail)
        );
    }

    private ApiException badRequest(String detail) {
        return new ApiException(
            HttpStatus.BAD_REQUEST,
            ApiErrorCode.BAD_REQUEST,
            "Invalid request",
            List.of(detail)
        );
    }

    private ApiException courseNotFound() {
        return new ApiException(
            HttpStatus.NOT_FOUND, ApiErrorCode.COURSE_NOT_FOUND, "Course not found");
    }

    private ApiException sessionNotFound() {
        return new ApiException(
            HttpStatus.NOT_FOUND, ApiErrorCode.COURSE_SESSION_NOT_FOUND,
            "Course session not found");
    }

    private ApiException sessionConflict() {
        return new ApiException(
            HttpStatus.CONFLICT, ApiErrorCode.COURSE_SESSION_CONFLICT,
            "Course session conflict");
    }
}
