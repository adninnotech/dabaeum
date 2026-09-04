package com.adn.dabaeum.enrollment.api;

import com.adn.dabaeum.common.api.ApiErrorCode;
import com.adn.dabaeum.common.api.ApiException;
import com.adn.dabaeum.common.api.ApiMeta;
import com.adn.dabaeum.common.api.PageMeta;
import com.adn.dabaeum.enrollment.application.InstitutionEnrollmentPage;
import com.adn.dabaeum.enrollment.application.MyEnrollmentPage;
import com.adn.dabaeum.enrollment.domain.EnrollmentStatus;
import com.adn.dabaeum.enrollment.domain.InstitutionEnrollmentView;
import com.adn.dabaeum.enrollment.domain.MyEnrollmentView;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class EnrollmentQueryApiMapper {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final Set<String> ALLOWED_SORTS = Set.of(
        "appliedAt,asc", "appliedAt,desc", "createdAt,asc", "createdAt,desc");

    private final Clock clock;

    public EnrollmentQueryApiMapper(Clock clock) {
        this.clock = clock;
    }

    public record PageQuery(int page, int size, String sort, EnrollmentStatus status) {
    }

    public PageQuery toPageQuery(int page, int size, String sort, String status) {
        if (page < 0 || size < 1 || size > 100 || sort == null || sort.isBlank()
            || page > Integer.MAX_VALUE / size
            || !ALLOWED_SORTS.contains(sort.trim())) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                ApiErrorCode.BAD_REQUEST,
                "Request parameter is invalid",
                List.of("page", "size", "sort"));
        }
        EnrollmentStatus parsedStatus = null;
        if (status != null && !status.isBlank()) {
            try {
                parsedStatus = EnrollmentStatus.valueOf(status.trim());
            } catch (IllegalArgumentException exception) {
                throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    ApiErrorCode.BAD_REQUEST,
                    "Request parameter is invalid",
                    List.of("status"));
            }
        }
        return new PageQuery(page, size, sort.trim(), parsedStatus);
    }

    public MyEnrollmentPageResponse toMyPageResponse(
        MyEnrollmentPage page, String requestId
    ) {
        return new MyEnrollmentPageResponse(
            page.data().stream().map(this::toResponse).toList(),
            new PageMeta(page.page(), page.size(), page.totalElements(), page.totalPages()),
            meta(requestId));
    }

    public InstitutionEnrollmentPageResponse toInstitutionPageResponse(
        InstitutionEnrollmentPage page, String requestId
    ) {
        return new InstitutionEnrollmentPageResponse(
            page.data().stream().map(this::toResponse).toList(),
            new PageMeta(page.page(), page.size(), page.totalElements(), page.totalPages()),
            meta(requestId));
    }

    private MyEnrollmentResponse toResponse(MyEnrollmentView view) {
        return new MyEnrollmentResponse(
            view.id(), view.courseId(), view.userId(), view.status(),
            view.appliedAt(), view.createdAt(), view.courseTitle(), view.courseCode(),
            view.courseStatus(), view.institutionName());
    }

    private InstitutionEnrollmentResponse toResponse(InstitutionEnrollmentView view) {
        return new InstitutionEnrollmentResponse(
            view.id(), view.courseId(), view.userId(), view.status(),
            view.appliedAt(), view.createdAt(), view.courseTitle(), view.userName());
    }

    public String parseLearningStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        String trimmed = status.trim();
        if (!Set.of("APPLIED", "IN_PROGRESS", "COMPLETED").contains(trimmed)) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST, ApiErrorCode.BAD_REQUEST,
                "Request parameter is invalid", List.of("status"));
        }
        return trimmed;
    }

    public void validatePaging(int page, int size) {
        if (page < 0 || size < 1 || size > 100 || page > Integer.MAX_VALUE / size) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST, ApiErrorCode.BAD_REQUEST,
                "Request parameter is invalid", List.of("page", "size"));
        }
    }

    public LearningCoursePageResponse toLearningCoursePageResponse(
        com.adn.dabaeum.enrollment.application.LearningCoursePage page, String requestId
    ) {
        return new LearningCoursePageResponse(
            page.data().stream()
                .map(view -> new LearningCourseResponse(
                    view.enrollmentId(), view.courseId(), view.title(),
                    view.institutionName(), view.learningStatus(),
                    view.educationType(), view.startDate(), view.endDate(),
                    view.appliedAt()))
                .toList(),
            new PageMeta(page.page(), page.size(), page.totalElements(), page.totalPages()),
            meta(requestId));
    }

    public EnrollmentProgressPageResponse toProgressPageResponse(
        com.adn.dabaeum.enrollment.application.EnrollmentProgressPage page,
        String requestId
    ) {
        return new EnrollmentProgressPageResponse(
            page.data().stream().map(this::toProgressResponse).toList(),
            new PageMeta(page.page(), page.size(), page.totalElements(), page.totalPages()),
            meta(requestId));
    }

    public EnrollmentProgressResponse toProgressResponse(
        com.adn.dabaeum.enrollment.domain.EnrollmentProgressView view
    ) {
        return new EnrollmentProgressResponse(
            view.enrollmentId(), view.userId(), view.userName(), view.courseId(),
            view.courseTitle(), view.attendedCount(), view.sessionCount(),
            view.progressPercent(), view.status());
    }

    public ApiMeta meta(String requestId) {
        return new ApiMeta(requestId, OffsetDateTime.ofInstant(clock.instant(), SEOUL));
    }
}
