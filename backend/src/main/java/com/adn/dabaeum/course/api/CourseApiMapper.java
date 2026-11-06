package com.adn.dabaeum.course.api;

import org.springframework.beans.factory.ObjectProvider;
import com.adn.dabaeum.classification.application.CourseClassificationService;
import com.adn.dabaeum.common.api.ApiErrorCode;
import com.adn.dabaeum.common.api.ApiException;
import com.adn.dabaeum.common.api.ApiMeta;
import com.adn.dabaeum.common.api.ApiResponse;
import com.adn.dabaeum.common.api.DeletionResponse;
import com.adn.dabaeum.common.api.KeywordQuery;
import com.adn.dabaeum.common.text.RichText;
import com.adn.dabaeum.course.application.CourseUpdateField;
import com.adn.dabaeum.course.application.CoursePage;
import com.adn.dabaeum.course.application.CreateCourseCommand;
import com.adn.dabaeum.course.application.ListCoursesQuery;
import com.adn.dabaeum.course.application.UpdateCourseCommand;
import com.adn.dabaeum.course.domain.Course;
import com.adn.dabaeum.course.domain.CourseDetail;
import com.adn.dabaeum.course.domain.CourseView;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class CourseApiMapper {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final Clock clock;

    // 분류 서비스는 local/dev 에만 있다. 없으면(컨트롤러 테스트) 경로는 빈 배열.
    private final ObjectProvider<CourseClassificationService> classifications;

    public CourseApiMapper(Clock clock, ObjectProvider<CourseClassificationService> classifications) {
        this.classifications = classifications;
        this.clock = clock;
    }

    public CreateCourseCommand toCommand(CourseCreateRequest request) {
        return new CreateCourseCommand(
            request.institutionId(),
            request.courseCode(),
            request.title(),
            RichText.sanitize(request.description()),
            request.classificationCode(),
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
            request.thumbnailFileId(),
            toDetail(request.detail())
        );
    }

    public ListCoursesQuery toListQuery(
        int page, int size, String sort, String q,
        com.adn.dabaeum.course.domain.CourseEducationType educationType,
        com.adn.dabaeum.course.domain.CourseSelectionMethod selectionMethod,
        com.adn.dabaeum.course.domain.CourseStatus status,
        String classificationCode
    ) {
        return new ListCoursesQuery(
            page, size, sort, KeywordQuery.normalize(q), educationType, selectionMethod, status,
            classificationCode);
    }

    public UpdateCourseCommand toUpdateCommand(
        UUID courseId,
        CourseUpdateRequest request
    ) {
        return new UpdateCourseCommand(
            courseId,
            request.courseCodeUpdate(),
            request.titleUpdate(),
            request.descriptionUpdate().map(RichText::sanitize),
            request.classificationCodeUpdate(),
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
            request.thumbnailFileIdUpdate(),
            toDetailUpdate(request.detailUpdate())
        );
    }

    private CourseUpdateField<CourseDetail> toDetailUpdate(
        CourseUpdateField<CourseDetailPayload> field
    ) {
        return field.present()
            ? CourseUpdateField.present(toDetail(field.value()))
            : CourseUpdateField.absent();
    }

    private CourseDetail toDetail(CourseDetailPayload payload) {
        if (payload == null) {
            return CourseDetail.empty();
        }
        try {
            return payload.toDomain();
        } catch (IllegalArgumentException exception) {
            throw new ApiException(
                HttpStatus.UNPROCESSABLE_CONTENT, ApiErrorCode.VALIDATION_FAILED,
                "Request validation failed",
                List.of("detail." + fieldOf(exception)));
        }
    }

    /** 도메인 메시지 "tuitionAmount is required when type is PAID" 의 첫 단어. */
    private String fieldOf(IllegalArgumentException exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return "detail";
        }
        return message.split(" ", 2)[0];
    }

    public ApiResponse<CourseResponse> toApiResponse(
        Course course,
        String requestId
    ) {
        return toApiResponse(new CourseView(course, 0, null, null), requestId);
    }

    public ApiResponse<CourseResponse> toApiResponse(
        CourseView view,
        String requestId
    ) {
        return new ApiResponse<>(
            toResponse(view),
            new ApiMeta(
                requestId,
                OffsetDateTime.ofInstant(clock.instant(), SEOUL)
            )
        );
    }

    public ApiResponse<DeletionResponse> toDeletionResponse(UUID courseId, String requestId) {
        return new ApiResponse<>(
            new DeletionResponse(courseId),
            new ApiMeta(requestId, OffsetDateTime.ofInstant(clock.instant(), SEOUL))
        );
    }

    public CourseResponse toResponse(Course course) {
        return toResponse(new CourseView(course, 0, null, null));
    }

    public CourseResponse toResponse(CourseView view) {
        Course course = view.course();
        return new CourseResponse(
            course.id(),
            course.institutionId(),
            course.courseCode(),
            course.title(),
            course.description(),
            course.classificationCode(), classificationPath(course.classificationCode()),
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
            course.thumbnailFileId(),
            CourseDetailPayload.from(course.detail()),
            view.enrolledCount(),
            view.institutionName(),
            view.mainInstructorName()
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
    private List<String> classificationPath(String code) {
        CourseClassificationService service = classifications.getIfAvailable();
        return service == null ? List.of() : service.pathOf(code);
    }
}

