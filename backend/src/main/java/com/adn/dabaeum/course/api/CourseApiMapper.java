package com.adn.dabaeum.course.api;

import com.adn.dabaeum.common.api.ApiMeta;
import com.adn.dabaeum.common.api.ApiResponse;
import com.adn.dabaeum.course.application.CoursePage;
import com.adn.dabaeum.course.application.CreateCourseCommand;
import com.adn.dabaeum.course.application.ListCoursesQuery;
import com.adn.dabaeum.course.application.UpdateCourseCommand;
import com.adn.dabaeum.course.domain.Course;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class CourseApiMapper {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final Clock clock;

    public CourseApiMapper(Clock clock) {
        this.clock = clock;
    }

    public CreateCourseCommand toCommand(CourseCreateRequest request) {
        return new CreateCourseCommand(
            request.institutionId(),
            request.courseCode(),
            request.title(),
            request.description(),
            request.category(),
            request.educationType(),
            request.startDate(),
            request.endDate(),
            request.recruitStartDate(),
            request.recruitEndDate(),
            request.capacity(),
            request.location(),
            request.onlineUrl(),
            request.creditBankEligible(),
            request.creditValue(),
            request.thumbnailFileId()
        );
    }

    public ListCoursesQuery toListQuery(int page, int size, String sort) {
        return new ListCoursesQuery(page, size, sort);
    }

    public UpdateCourseCommand toUpdateCommand(
        UUID courseId,
        CourseUpdateRequest request
    ) {
        return new UpdateCourseCommand(
            courseId,
            request.courseCodeUpdate(),
            request.titleUpdate(),
            request.descriptionUpdate(),
            request.categoryUpdate(),
            request.educationTypeUpdate(),
            request.startDateUpdate(),
            request.endDateUpdate(),
            request.recruitStartDateUpdate(),
            request.recruitEndDateUpdate(),
            request.capacityUpdate(),
            request.locationUpdate(),
            request.onlineUrlUpdate(),
            request.creditBankEligibleUpdate(),
            request.creditValueUpdate(),
            request.statusUpdate(),
            request.thumbnailFileIdUpdate()
        );
    }

    public ApiResponse<CourseResponse> toApiResponse(
        Course course,
        String requestId
    ) {
        return new ApiResponse<>(
            toResponse(course),
            new ApiMeta(
                requestId,
                OffsetDateTime.ofInstant(clock.instant(), SEOUL)
            )
        );
    }

    public CourseResponse toResponse(Course course) {
        return new CourseResponse(
            course.id(),
            course.institutionId(),
            course.courseCode(),
            course.title(),
            course.description(),
            course.category(),
            course.educationType(),
            course.startDate(),
            course.endDate(),
            course.recruitStartDate(),
            course.recruitEndDate(),
            course.capacity(),
            course.location(),
            course.onlineUrl(),
            course.creditBankEligible(),
            course.creditValue(),
            course.status(),
            course.createdAt(),
            course.updatedAt(),
            course.thumbnailFileId()
        );
    }

    public CoursePageResponse toPageResponse(CoursePage page, String requestId) {
        List<CourseResponse> data = page.data()
            .stream()
            .map(this::toResponse)
            .toList();
        return new CoursePageResponse(
            data,
            new com.adn.dabaeum.common.api.PageMeta(
                page.page(),
                page.size(),
                page.totalElements(),
                page.totalPages()
            ),
            new ApiMeta(
                requestId,
                OffsetDateTime.ofInstant(clock.instant(), SEOUL)
            )
        );
    }
}
