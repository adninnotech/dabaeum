package com.adn.dabaeum.course.api;

import com.adn.dabaeum.common.api.ApiErrorCode;
import com.adn.dabaeum.common.api.ApiException;
import com.adn.dabaeum.common.api.ApiMeta;
import com.adn.dabaeum.common.api.PageMeta;
import com.adn.dabaeum.course.application.InstitutionInstructorPage;
import com.adn.dabaeum.course.application.InstructorCoursePage;
import com.adn.dabaeum.course.domain.CourseStatus;
import com.adn.dabaeum.course.domain.InstitutionInstructorView;
import com.adn.dabaeum.course.domain.InstructorCourseStats;
import com.adn.dabaeum.course.domain.InstructorCourseView;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class CourseQueryApiMapper {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final Set<String> ALLOWED_SORTS = Set.of(
        "createdAt,asc", "createdAt,desc", "title,asc", "title,desc",
        "startDate,asc", "startDate,desc");
    private static final Set<String> INSTRUCTOR_SORTS = Set.of(
        "name,asc", "name,desc", "joinedAt,asc", "joinedAt,desc");
    private static final Set<String> USER_STATUSES = Set.of(
        "ACTIVE", "DORMANT", "WITHDRAWN", "SUSPENDED");

    private final CourseApiMapper courseApiMapper;
    private final Clock clock;

    public CourseQueryApiMapper(CourseApiMapper courseApiMapper, Clock clock) {
        this.courseApiMapper = courseApiMapper;
        this.clock = clock;
    }

    public record CoursePageQuery(int page, int size, String sort, CourseStatus status) {
    }

    public record InstructorPageQuery(int page, int size, String sort, String status) {
    }

    public CoursePageQuery toCoursePageQuery(
        int page, int size, String sort, String status
    ) {
        validatePaging(page, size, sort, ALLOWED_SORTS);
        CourseStatus parsedStatus = null;
        if (status != null && !status.isBlank()) {
            try {
                parsedStatus = CourseStatus.valueOf(status.trim());
            } catch (IllegalArgumentException exception) {
                throw badRequest(List.of("status"));
            }
        }
        return new CoursePageQuery(page, size, sort.trim(), parsedStatus);
    }

    public InstructorPageQuery toInstructorPageQuery(
        int page, int size, String sort, String status
    ) {
        validatePaging(page, size, sort, INSTRUCTOR_SORTS);
        String parsedStatus = null;
        if (status != null && !status.isBlank()) {
            String trimmed = status.trim();
            if (!USER_STATUSES.contains(trimmed)) {
                throw badRequest(List.of("status"));
            }
            parsedStatus = trimmed;
        }
        return new InstructorPageQuery(page, size, sort.trim(), parsedStatus);
    }

    public InstructorCoursePageResponse toInstructorCoursePageResponse(
        InstructorCoursePage page, String requestId
    ) {
        return new InstructorCoursePageResponse(
            page.data().stream().map(this::toResponse).toList(),
            new PageMeta(page.page(), page.size(), page.totalElements(), page.totalPages()),
            meta(requestId));
    }

    public InstructorCourseStatsResponse toStatsResponse(InstructorCourseStats stats) {
        return new InstructorCourseStatsResponse(
            stats.total(), stats.recruiting(), stats.inProgress(), stats.completed());
    }

    public InstitutionInstructorPageResponse toInstitutionInstructorPageResponse(
        InstitutionInstructorPage page, String requestId
    ) {
        return new InstitutionInstructorPageResponse(
            page.data().stream().map(this::toResponse).toList(),
            new PageMeta(page.page(), page.size(), page.totalElements(), page.totalPages()),
            meta(requestId));
    }

    public ApiMeta meta(String requestId) {
        return new ApiMeta(requestId, OffsetDateTime.ofInstant(clock.instant(), SEOUL));
    }

    private InstructorCourseResponse toResponse(InstructorCourseView view) {
        return new InstructorCourseResponse(
            courseApiMapper.toResponse(view.course()),
            view.instructorRole(),
            view.enrolledCount());
    }

    private InstitutionInstructorResponse toResponse(InstitutionInstructorView view) {
        return new InstitutionInstructorResponse(
            view.userId(), view.name(), view.email(), view.phone(), view.status(),
            view.joinedAt(), view.courseCount(), view.memo());
    }

    private void validatePaging(int page, int size, String sort, Set<String> allowedSorts) {
        if (page < 0 || size < 1 || size > 100 || sort == null || sort.isBlank()
            || page > Integer.MAX_VALUE / size
            || !allowedSorts.contains(sort.trim())) {
            throw badRequest(List.of("page", "size", "sort"));
        }
    }

    private ApiException badRequest(List<String> fields) {
        return new ApiException(
            HttpStatus.BAD_REQUEST,
            ApiErrorCode.BAD_REQUEST,
            "Request parameter is invalid",
            fields);
    }
}
