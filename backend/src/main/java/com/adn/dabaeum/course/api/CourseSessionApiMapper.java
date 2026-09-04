package com.adn.dabaeum.course.api;

import com.adn.dabaeum.common.api.ApiMeta;
import com.adn.dabaeum.common.api.ApiResponse;
import com.adn.dabaeum.course.application.CourseSessionPage;
import com.adn.dabaeum.course.application.CreateCourseSessionCommand;
import com.adn.dabaeum.course.application.ListCourseSessionsQuery;
import com.adn.dabaeum.course.application.UpdateCourseSessionCommand;
import com.adn.dabaeum.course.domain.CourseSession;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
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
            request.endsAt(), request.location(), request.attendanceOpensAt(),
            request.attendanceClosesAt(), request.status());
    }

    public UpdateCourseSessionCommand toUpdateCommand(UUID sessionId, CourseSessionUpdateRequest request) {
        return new UpdateCourseSessionCommand(sessionId, request.sessionNoUpdate(), request.startsAtUpdate(),
            request.endsAtUpdate(), request.locationUpdate(), request.attendanceOpensAtUpdate(),
            request.attendanceClosesAtUpdate(), request.statusUpdate());
    }

    public ApiResponse<CourseSessionResponse> toApiResponse(CourseSession session, String requestId) {
        return new ApiResponse<>(toResponse(session), meta(requestId));
    }

    public CourseSessionResponse toResponse(CourseSession session) {
        return new CourseSessionResponse(session.id(), session.courseId(), session.sessionNo(),
            session.startsAt(), session.endsAt(), session.location(), session.attendanceOpensAt(),
            session.attendanceClosesAt(), session.status(), session.createdAt(), session.updatedAt());
    }

    public CourseSessionPageResponse toPageResponse(CourseSessionPage page, String requestId) {
        List<CourseSessionResponse> data = page.data().stream().map(this::toResponse).toList();
        return new CourseSessionPageResponse(data,
            new com.adn.dabaeum.common.api.PageMeta(page.page(), page.size(), page.totalElements(), page.totalPages()),
            meta(requestId));
    }

    private ApiMeta meta(String requestId) {
        return new ApiMeta(requestId, OffsetDateTime.ofInstant(clock.instant(), SEOUL));
    }
}
