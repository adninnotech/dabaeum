package com.adn.dabaeum.course.api;

import com.adn.dabaeum.common.api.ApiErrorCode;
import com.adn.dabaeum.common.api.ApiException;
import com.adn.dabaeum.common.api.ApiMeta;
import com.adn.dabaeum.common.api.ApiResponse;
import com.adn.dabaeum.course.application.CreateCourseSessionBulkCommand;
import com.adn.dabaeum.course.application.CourseSessionPage;
import com.adn.dabaeum.course.application.CreateCourseSessionCommand;
import com.adn.dabaeum.course.application.ListCourseSessionsQuery;
import com.adn.dabaeum.course.application.RepeatRule;
import com.adn.dabaeum.course.application.SessionDraft;
import com.adn.dabaeum.course.application.SessionScheduleExpander;
import com.adn.dabaeum.course.application.UpdateCourseSessionCommand;
import com.adn.dabaeum.course.domain.CourseSession;
import com.adn.dabaeum.course.domain.CourseSessionStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class CourseSessionApiMapper {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private final Clock clock;

    public CourseSessionApiMapper(Clock clock) { this.clock = clock; }

    public ListCourseSessionsQuery toListQuery(UUID courseId, int page, int size, String sort) {
        return new ListCourseSessionsQuery(courseId, page, size, sort);
    }

    public CreateCourseSessionCommand toCreateCommand(UUID courseId, CourseSessionCreateRequest request) {
        return new CreateCourseSessionCommand(courseId, request.sessionNo(), request.startsAt(),
            request.endsAt(), request.location(), request.topic(), request.attendanceOpensAt(),
            request.attendanceClosesAt(), request.status());
    }

    public UpdateCourseSessionCommand toUpdateCommand(UUID sessionId, CourseSessionUpdateRequest request) {
        return new UpdateCourseSessionCommand(sessionId, request.sessionNoUpdate(), request.startsAtUpdate(),
            request.endsAtUpdate(), request.locationUpdate(), request.topicUpdate(),
            request.attendanceOpensAtUpdate(), request.attendanceClosesAtUpdate(), request.statusUpdate());
    }

    public ApiResponse<CourseSessionResponse> toApiResponse(CourseSession session, String requestId) {
        return new ApiResponse<>(toResponse(session), meta(requestId));
    }

    public CourseSessionResponse toResponse(CourseSession session) {
        return new CourseSessionResponse(session.id(), session.courseId(), session.sessionNo(),
            session.startsAt(), session.endsAt(), session.location(), session.topic(),
            session.attendanceOpensAt(), session.attendanceClosesAt(), session.status(),
            session.createdAt(), session.updatedAt());
    }

    public CourseSessionPageResponse toPageResponse(CourseSessionPage page, String requestId) {
        List<CourseSessionResponse> data = page.data().stream().map(this::toResponse).toList();
        return new CourseSessionPageResponse(data,
            new com.adn.dabaeum.common.api.PageMeta(page.page(), page.size(), page.totalElements(), page.totalPages()),
            meta(requestId));
    }

    /**
     * {@code repeat} 또는 {@code sessions} 중 정확히 하나를 요구하고, 반복 규칙은 여기서
     * 서울 시간대 기준으로 {@link Instant} 로 변환한다 — 이 변환 지점을 여러 곳에 두지 않는다.
     */
    public CreateCourseSessionBulkCommand toBulkCommand(
        UUID courseId, CourseSessionBulkCreateRequest request) {
        boolean hasRepeat = request.repeat() != null;
        boolean hasSessions = request.sessions() != null && !request.sessions().isEmpty();
        if (hasRepeat == hasSessions) {
            throw new ApiException(
                HttpStatus.UNPROCESSABLE_CONTENT, ApiErrorCode.VALIDATION_FAILED,
                "Exactly one of repeat or sessions is required",
                List.of("repeat", "sessions"));
        }

        if (hasSessions && request.sessions().stream().anyMatch(Objects::isNull)) {
            throw new ApiException(
                HttpStatus.UNPROCESSABLE_CONTENT, ApiErrorCode.VALIDATION_FAILED,
                "Request validation failed", List.of("sessions"));
        }

        List<CreateCourseSessionCommand> sessions = hasRepeat
            ? expandRepeat(courseId, request.repeat())
            : request.sessions().stream()
                .map(sessionRequest -> toCreateCommand(courseId, sessionRequest))
                .toList();
        boolean replaceExisting = Boolean.TRUE.equals(request.replaceExisting());
        return new CreateCourseSessionBulkCommand(sessions, replaceExisting, hasRepeat && !replaceExisting);
    }

    public ApiResponse<List<CourseSessionResponse>> toBulkApiResponse(
        List<CourseSession> sessions, String requestId) {
        return new ApiResponse<>(sessions.stream().map(this::toResponse).toList(), meta(requestId));
    }

    private List<CreateCourseSessionCommand> expandRepeat(
        UUID courseId, CourseSessionBulkCreateRequest.RepeatPayload repeat) {
        RepeatRule rule = new RepeatRule(repeat.firstDate(), repeat.daysOfWeek(),
            repeat.startTime(), repeat.endTime(), repeat.count());
        List<SessionDraft> drafts;
        try {
            drafts = SessionScheduleExpander.expand(rule);
        } catch (IllegalArgumentException exception) {
            throw new ApiException(
                HttpStatus.UNPROCESSABLE_CONTENT, ApiErrorCode.VALIDATION_FAILED,
                "Request validation failed", List.of("repeat"));
        }
        return drafts.stream()
            .map(draft -> new CreateCourseSessionCommand(
                courseId, draft.sessionNo(),
                toSeoulInstant(draft.date(), draft.startTime()),
                toSeoulInstant(draft.date(), draft.endTime()),
                null, null, null, null, CourseSessionStatus.SCHEDULED))
            .toList();
    }

    private Instant toSeoulInstant(LocalDate date, LocalTime time) {
        return date.atTime(time).atZone(SEOUL).toInstant();
    }

    private ApiMeta meta(String requestId) {
        return new ApiMeta(requestId, OffsetDateTime.ofInstant(clock.instant(), SEOUL));
    }
}
