package com.adn.dabaeum.enrollment.api;

import com.adn.dabaeum.common.api.ApiMeta;
import com.adn.dabaeum.common.api.ApiResponse;
import com.adn.dabaeum.common.api.PageMeta;
import com.adn.dabaeum.enrollment.application.BulkDecisionCommand;
import com.adn.dabaeum.enrollment.application.BulkDecisionResult;
import com.adn.dabaeum.enrollment.application.CreateEnrollmentCommand;
import com.adn.dabaeum.enrollment.application.CreateProxyEnrollmentCommand;
import com.adn.dabaeum.enrollment.application.EnrollmentPage;
import com.adn.dabaeum.enrollment.application.ListCourseEnrollmentsQuery;
import com.adn.dabaeum.enrollment.application.RejectEnrollmentCommand;
import com.adn.dabaeum.enrollment.domain.Enrollment;
import com.adn.dabaeum.enrollment.domain.EnrollmentListItem;
import java.time.Clock;
import java.time.Instant;
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

    public BulkDecisionCommand toBulkDecisionCommand(EnrollmentBulkDecisionRequest request) {
        return new BulkDecisionCommand(
            request.enrollmentIds(), request.decision(), request.reason());
    }

    public ApiResponse<EnrollmentResponse> toApiResponse(Enrollment enrollment, String requestId) {
        return new ApiResponse<>(toResponse(enrollment), meta(requestId));
    }

    /** 성공·실패 목록뿐이라 따로 응답 타입을 두지 않고 결과를 그대로 싣는다. */
    public ApiResponse<BulkDecisionResult> toApiResponse(
        BulkDecisionResult result,
        String requestId
    ) {
        return new ApiResponse<>(result, meta(requestId));
    }

    public EnrollmentResponse toResponse(Enrollment enrollment) {
        return new EnrollmentResponse(enrollment.id(), enrollment.courseId(), enrollment.userId(),
            enrollment.appliedBy(), enrollment.applicationType(), enrollment.status(),
            enrollment.appliedAt(), enrollment.approvedAt(), enrollment.rejectedAt(),
            enrollment.cancelledAt(), enrollment.withdrawnAt(), enrollment.rejectionReason(),
            enrollment.cancellationReason(), enrollment.createdAt(), enrollment.updatedAt(),
            processedAt(enrollment), null, 0, null, null);
    }

    public EnrollmentResponse toResponse(EnrollmentListItem item) {
        Enrollment enrollment = item.enrollment();
        return new EnrollmentResponse(enrollment.id(), enrollment.courseId(), enrollment.userId(),
            enrollment.appliedBy(), enrollment.applicationType(), enrollment.status(),
            enrollment.appliedAt(), enrollment.approvedAt(), enrollment.rejectedAt(),
            enrollment.cancelledAt(), enrollment.withdrawnAt(), enrollment.rejectionReason(),
            enrollment.cancellationReason(), enrollment.createdAt(), enrollment.updatedAt(),
            processedAt(enrollment), item.waitlistPosition(), item.progressPercent(),
            item.lastStudiedAt(), item.profile());
    }

    public EnrollmentPageResponse toPageResponse(EnrollmentPage page, String requestId) {
        List<EnrollmentResponse> data = page.data().stream().map(this::toResponse).toList();
        return new EnrollmentPageResponse(data,
            new PageMeta(page.page(), page.size(), page.totalElements(), page.totalPages()),
            meta(requestId));
    }

    /** withdrawnAt·cancelledAt·rejectedAt·approvedAt 중 마지막으로 일어난 처리의 시각.
     * 승인 후 취소(WITHDRAWN)는 approvedAt 과 withdrawnAt 이 둘 다 있는데,
     * 화면에 보여야 할 것은 마지막 처리인 취소다. */
    private static Instant processedAt(Enrollment enrollment) {
        if (enrollment.withdrawnAt() != null) {
            return enrollment.withdrawnAt();
        }
        if (enrollment.cancelledAt() != null) {
            return enrollment.cancelledAt();
        }
        if (enrollment.rejectedAt() != null) {
            return enrollment.rejectedAt();
        }
        return enrollment.approvedAt();
    }

    private ApiMeta meta(String requestId) {
        return new ApiMeta(requestId, OffsetDateTime.ofInstant(clock.instant(), SEOUL));
    }
}
