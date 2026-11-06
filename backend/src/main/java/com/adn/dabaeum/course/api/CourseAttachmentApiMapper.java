package com.adn.dabaeum.course.api;

import com.adn.dabaeum.common.api.ApiMeta;
import com.adn.dabaeum.common.api.ApiResponse;
import com.adn.dabaeum.course.application.AttachCourseAttachmentCommand;
import com.adn.dabaeum.course.domain.CourseAttachment;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class CourseAttachmentApiMapper {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final Clock clock;

    public CourseAttachmentApiMapper(Clock clock) {
        this.clock = clock;
    }

    public AttachCourseAttachmentCommand toAttachCommand(
        UUID courseId,
        CourseAttachmentAttachRequest request
    ) {
        return new AttachCourseAttachmentCommand(courseId, request.fileId());
    }

    public ApiResponse<CourseAttachmentResponse> toApiResponse(
        CourseAttachment attachment,
        String requestId
    ) {
        return new ApiResponse<>(toResponse(attachment), meta(requestId));
    }

    public ApiResponse<List<CourseAttachmentResponse>> toListResponse(
        List<CourseAttachment> attachments,
        String requestId
    ) {
        return new ApiResponse<>(
            attachments.stream().map(this::toResponse).toList(),
            meta(requestId)
        );
    }

    private CourseAttachmentResponse toResponse(CourseAttachment attachment) {
        return new CourseAttachmentResponse(
            attachment.id(), attachment.courseId(), attachment.fileId(),
            attachment.fileName(), attachment.byteSize(), attachment.sortOrder(),
            attachment.createdAt()
        );
    }

    private ApiMeta meta(String requestId) {
        return new ApiMeta(requestId, OffsetDateTime.ofInstant(clock.instant(), SEOUL));
    }
}
