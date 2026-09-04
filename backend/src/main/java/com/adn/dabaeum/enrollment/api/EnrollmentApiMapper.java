package com.adn.dabaeum.enrollment.api;

import com.adn.dabaeum.common.api.ApiMeta;
import com.adn.dabaeum.common.api.ApiResponse;
import com.adn.dabaeum.common.api.PageMeta;
import com.adn.dabaeum.enrollment.application.CreateEnrollmentCommand;
import com.adn.dabaeum.enrollment.application.CreateProxyEnrollmentCommand;
import com.adn.dabaeum.enrollment.application.EnrollmentPage;
import com.adn.dabaeum.enrollment.application.ListCourseEnrollmentsQuery;
import com.adn.dabaeum.enrollment.application.RejectEnrollmentCommand;
import com.adn.dabaeum.enrollment.domain.Enrollment;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class EnrollmentApiMapper {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private final Clock clock;

    public EnrollmentApiMapper(Clock clock) {
        this.clock = clock;
    }

    public CreateEnrollmentCommand toCreateCommand(UUID courseId, EnrollmentCreateRequest request) {
        return new CreateEnrollmentCommand(courseId, request.userId(), request.applicationType());
    }

    public CreateProxyEnrollmentCommand toProxyCommand(UUID courseId,
        ProxyEnrollmentCreateRequest request) {
        return new CreateProxyEnrollmentCommand(courseId, request.userId());
    }

    public ListCourseEnrollmentsQuery toListQuery(UUID courseId, int page, int size, String sort) {
        return new ListCourseEnrollmentsQuery(courseId, page, size, sort);
    }

    public RejectEnrollmentCommand toRejectCommand(
        UUID enrollmentId,
        EnrollmentRejectionRequest request
    ) {
        return new RejectEnrollmentCommand(enrollmentId, request.reason());
    }

    public ApiResponse<EnrollmentResponse> toApiResponse(Enrollment enrollment, String requestId) {
        return new ApiResponse<>(toResponse(enrollment), meta(requestId));
    }

    public EnrollmentResponse toResponse(Enrollment enrollment) {
        return new EnrollmentResponse(enrollment.id(), enrollment.courseId(), enrollment.userId(),
            enrollment.appliedBy(), enrollment.applicationType(), enrollment.status(),
            enrollment.appliedAt(), enrollment.approvedAt(), enrollment.rejectedAt(),
            enrollment.cancelledAt(), enrollment.withdrawnAt(), enrollment.rejectionReason(),
            enrollment.cancellationReason(), enrollment.createdAt(), enrollment.updatedAt());
    }

    public EnrollmentPageResponse toPageResponse(EnrollmentPage page, String requestId) {
        List<EnrollmentResponse> data = page.data().stream().map(this::toResponse).toList();
        return new EnrollmentPageResponse(data,
            new PageMeta(page.page(), page.size(), page.totalElements(), page.totalPages()),
            meta(requestId));
    }

    private ApiMeta meta(String requestId) {
        return new ApiMeta(requestId, OffsetDateTime.ofInstant(clock.instant(), SEOUL));
    }
}
