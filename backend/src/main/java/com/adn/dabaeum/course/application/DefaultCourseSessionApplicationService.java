package com.adn.dabaeum.course.application;

import com.adn.dabaeum.authorization.application.AuthorizationPolicy;
import com.adn.dabaeum.common.api.ApiErrorCode;
import com.adn.dabaeum.common.api.ApiException;
import com.adn.dabaeum.common.api.SortParameter;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DefaultCourseSessionApplicationService
    implements CourseSessionApplicationService {

    /**
     * 재정렬 1단계에서 회차 번호를 밀어두는 오프셋. 모든 행에 같은 값을 더하는 것은
     * 원래 값들의 유일성을 보존하므로, 한 강좌의 회차 수가 이 값을 넘지 않는 한
     * 밀어낸 번호대(offset+1..offset+N)는 목표 번호대(1..N)와 절대 겹치지 않는다.
     */
    private static final int SESSION_NO_REORDER_OFFSET = 10_000;

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
            || command.status() == null) {
            throw validation("requestBody");
        }
        if ((command.startsAt() == null) != (command.endsAt() == null)) {
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
                now,
                command.topic()
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
    @Transactional
    public List<CourseSession> createBulk(
        UUID courseId,
        CreateCourseSessionBulkCommand command,
        AuthenticatedUserContext context
    ) {
        if (courseId == null || command == null || command.sessions() == null
            || command.sessions().isEmpty()) {
            throw validation("requestBody");
        }
        Course course = findCourse(courseId);
        requireManager(context, course);

        if (command.replaceExisting()) {
            if (sessionRepository.existsSessionDependenciesByCourseId(courseId)) {
                throw sessionDependencyConflict();
            }
            if (sessionRepository.existsNonScheduledByCourseId(courseId)) {
                throw sessionStatusConflict();
            }
            try {
                sessionRepository.deleteAllByCourseId(courseId);
            } catch (DataIntegrityViolationException exception) {
                // ponytail: 확인과 삭제 사이 경합으로 FK RESTRICT 에 걸리면 여기서 409 로 잡는다
                // (락 대신 catch 로 처리 — 발생 빈도가 낮아 락 비용이 더 크다)
                throw sessionDependencyConflict();
            }
        }

        Instant now = clock.instant();
        // 반복 규칙을 기존 회차 뒤에 붙일 때는 마지막 번호 다음부터 매긴다(QA CRS-10, 전에는 1부터라 409).
        int offset = command.appendAfterExisting()
            ? sessionRepository.findByCourseId(courseId).stream()
                .mapToInt(CourseSession::sessionNo).max().orElse(0)
            : 0;
        List<CourseSession> created = new ArrayList<>(command.sessions().size());
        for (CreateCourseSessionCommand requested : command.sessions()) {
            CreateCourseSessionCommand sessionCommand = offset == 0 ? requested
                : new CreateCourseSessionCommand(requested.courseId(), requested.sessionNo() + offset,
                    requested.startsAt(), requested.endsAt(), requested.location(), requested.topic(),
                    requested.attendanceOpensAt(), requested.attendanceClosesAt(), requested.status());
            if (sessionRepository.findByCourseIdAndSessionNo(
                courseId, sessionCommand.sessionNo()).isPresent()) {
                throw sessionConflict();
            }
            CourseSession session;
            try {
                session = new CourseSession(
                    Objects.requireNonNull(idGenerator.generate(), "generated id"),
                    courseId,
                    sessionCommand.sessionNo(),
                    sessionCommand.startsAt(),
                    sessionCommand.endsAt(),
                    sessionCommand.location(),
                    sessionCommand.attendanceOpensAt(),
                    sessionCommand.attendanceClosesAt(),
                    sessionCommand.status(),
                    now,
                    now,
                    sessionCommand.topic()
                );
                sessionRepository.save(session);
            } catch (IllegalArgumentException exception) {
                throw validation("requestBody");
            } catch (DataIntegrityViolationException exception) {
                throw sessionConflict();
            }
            created.add(session);
        }
        return created;
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
                clock.instant(),
                valueOrExisting(command.topic(), existing.topic())
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
            existing.attendanceClosesAt(), target, existing.createdAt(), now, existing.topic());
        if (!sessionRepository.reopen(sessionId, existing.status(), target, now)) {
            throw sessionNotFound();
        }
        corrections.insert(new AdminCorrection(
            UUID.randomUUID(), CorrectionTargetType.COURSE_SESSION, sessionId,
            existing.status().name(), target.name(), correctionReason, context.userId(), now));
        return updated;
    }

    @Override
    @Transactional
    public List<CourseSession> reorder(
        UUID courseId, List<UUID> sessionIds, AuthenticatedUserContext context
    ) {
        if (courseId == null || sessionIds == null || sessionIds.isEmpty()
            || sessionIds.stream().anyMatch(Objects::isNull)) {
            throw validation("sessionIds");
        }
        Course course = findCourse(courseId);
        requireManager(context, course);

        List<CourseSession> current = sessionRepository.findByCourseId(courseId);
        requireSamePermutation(current, sessionIds);

        // 유니크 제약(course_id, session_no) 때문에 바로 못 바꾼다.
        // 오프셋 구간으로 한 번 밀어낸 뒤 목표 번호를 준다.
        int shifted = sessionRepository.shiftSessionNoByOffset(courseId, SESSION_NO_REORDER_OFFSET);
        if (shifted != current.size()) {
            // 스냅샷을 뜬 뒤 동시에 회차가 지워지는 등으로 행 수가 어긋나면, 남은
            // updateSessionNo 가 목표 번호를 못 받아 오프셋 상태로 남을 수 있다.
            // 조용히 넘어가지 말고 트랜잭션을 되돌린다.
            throw sessionConflict();
        }
        for (int index = 0; index < sessionIds.size(); index++) {
            if (!sessionRepository.updateSessionNo(sessionIds.get(index), index + 1)) {
                throw sessionNotFound();
            }
        }
        return sessionRepository.findByCourseId(courseId);
    }

    private void requireSamePermutation(
        List<CourseSession> current, List<UUID> requested
    ) {
        Set<UUID> currentIds = current.stream()
            .map(CourseSession::id)
            .collect(Collectors.toSet());
        Set<UUID> requestedIds = Set.copyOf(requested);
        if (requested.size() != requestedIds.size()
            || !currentIds.equals(requestedIds)) {
            throw new ApiException(
                HttpStatus.UNPROCESSABLE_CONTENT, ApiErrorCode.VALIDATION_FAILED,
                "sessionIds must contain every session of the course exactly once",
                List.of("sessionIds"));
        }
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
        return SortParameter.split(value).orElseThrow(() -> badRequest("sort"));
    }

    private <T> CourseSessionUpdateField<T> field(CourseSessionUpdateField<T> value) {
        return value == null ? CourseSessionUpdateField.absent() : value;
    }

    private <T> T valueOrExisting(CourseSessionUpdateField<T> field, T existing) {
        return field.present() ? field.value() : existing;
    }

    private ApiException validation(String detail) {
        return new ApiException(
            HttpStatus.UNPROCESSABLE_CONTENT,
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

    private ApiException sessionDependencyConflict() {
        return new ApiException(
            HttpStatus.CONFLICT, ApiErrorCode.COURSE_SESSION_CONFLICT,
            "Course has sessions with recorded attendance or issued QR tokens; "
                + "the schedule cannot be recreated");
    }

    private ApiException sessionStatusConflict() {
        return new ApiException(
            HttpStatus.CONFLICT, ApiErrorCode.COURSE_SESSION_CONFLICT,
            "Course has sessions that are in progress, completed, or cancelled; "
                + "the schedule cannot be recreated");
    }
}
