package com.adn.dabaeum.course.api;

import com.adn.dabaeum.common.api.ApiMeta;
import com.adn.dabaeum.common.api.ApiResponse;
import com.adn.dabaeum.course.application.AssignCourseInstructorCommand;
import com.adn.dabaeum.course.application.CourseInstructorView;
import com.adn.dabaeum.course.application.UpdateCourseInstructorCommand;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class CourseInstructorApiMapper {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final Clock clock;

    public CourseInstructorApiMapper(Clock clock) {
        this.clock = clock;
    }

    public AssignCourseInstructorCommand toAssignCommand(
        UUID courseId,
        CourseInstructorAssignRequest request
    ) {
        return new AssignCourseInstructorCommand(
            courseId,
            request.userId(),
            request.role()
        );
    }

    public UpdateCourseInstructorCommand toUpdateCommand(
        UUID courseId,
        UUID userId,
        CourseInstructorUpdateRequest request
    ) {
        return new UpdateCourseInstructorCommand(
            courseId,
            userId,
            request.role()
        );
    }

    public ApiResponse<CourseInstructorResponse> toApiResponse(
        CourseInstructorView view,
        String requestId
    ) {
        return new ApiResponse<>(toResponse(view), meta(requestId));
    }

    /** 비로그인 공개 조회({@code includeEmail=false})에는 강사 이메일을 싣지 않는다. */
    public ApiResponse<List<CourseInstructorResponse>> toListResponse(
        List<CourseInstructorView> views,
        String requestId,
        boolean includeEmail
    ) {
        return new ApiResponse<>(
            views.stream()
                .map(this::toResponse)
                .map(response -> includeEmail ? response : withoutEmail(response))
                .toList(),
            meta(requestId)
        );
    }

    private static CourseInstructorResponse withoutEmail(CourseInstructorResponse response) {
        return new CourseInstructorResponse(
            response.id(), response.courseId(), response.userId(), response.instructorName(),
            null, response.role(), response.assignedAt(), response.createdAt(),
            response.introduction(), response.career(), response.profileImageId()
        );
    }

    private CourseInstructorResponse toResponse(CourseInstructorView view) {
        return new CourseInstructorResponse(
            view.id(), view.courseId(), view.userId(), view.instructorName(),
            view.instructorEmail(), view.role(),
            view.assignedAt(), view.createdAt(),
            view.introduction(), view.career(), view.profileImageId()
        );
    }

    private ApiMeta meta(String requestId) {
        return new ApiMeta(
            requestId,
            OffsetDateTime.ofInstant(clock.instant(), SEOUL)
        );
    }
}
