package com.adn.dabaeum.instructor.api;

import com.adn.dabaeum.common.api.ApiMeta;
import com.adn.dabaeum.common.api.ApiResponse;
import com.adn.dabaeum.common.api.PageMeta;
import com.adn.dabaeum.instructor.application.ApplyInstructorCommand;
import com.adn.dabaeum.instructor.application.InstructorApplicationPage;
import com.adn.dabaeum.instructor.application.InstructorApplicationView;
import com.adn.dabaeum.instructor.application.ListInstructorApplicationsQuery;
import com.adn.dabaeum.instructor.application.RejectInstructorApplicationCommand;
import com.adn.dabaeum.instructor.domain.InstructorApplicationStatus;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class InstructorApplicationApiMapper {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final Clock clock;

    public InstructorApplicationApiMapper(Clock clock) {
        this.clock = clock;
    }

    public ApplyInstructorCommand toApplyCommand(
        UUID institutionId,
        InstructorApplicationRequest request
    ) {
        return new ApplyInstructorCommand(
            institutionId,
            request == null ? null : request.applicationMessage()
        );
    }

    public RejectInstructorApplicationCommand toRejectCommand(
        UUID applicationId,
        InstructorApplicationRejectRequest request
    ) {
        return new RejectInstructorApplicationCommand(
            applicationId,
            request.rejectionReason()
        );
    }

    public ListInstructorApplicationsQuery toListQuery(
        UUID institutionId,
        InstructorApplicationStatus status,
        int page,
        int size,
        String sort
    ) {
        return new ListInstructorApplicationsQuery(
            institutionId,
            status,
            page,
            size,
            sort
        );
    }

    public ApiResponse<InstructorApplicationResponse> toApiResponse(
        InstructorApplicationView view,
        String requestId
    ) {
        return new ApiResponse<>(toResponse(view), meta(requestId));
    }

    public InstructorApplicationPageResponse toPageResponse(
        InstructorApplicationPage page,
        String requestId
    ) {
        return new InstructorApplicationPageResponse(
            page.data().stream().map(this::toResponse).toList(),
            new PageMeta(
                page.page(),
                page.size(),
                page.totalElements(),
                page.totalPages()
            ),
            meta(requestId)
        );
    }

    private InstructorApplicationResponse toResponse(
        InstructorApplicationView view
    ) {
        return new InstructorApplicationResponse(
            view.id(),
            view.userId(),
            view.applicantName(),
            view.applicantEmail(),
            view.applicantPhone(),
            view.institutionId(),
            view.status(),
            view.applicationMessage(),
            view.rejectionReason(),
            view.reviewedBy(),
            view.appliedAt(),
            view.reviewedAt(),
            view.createdAt(),
            view.updatedAt()
        );
    }

    private ApiMeta meta(String requestId) {
        return new ApiMeta(
            requestId,
            OffsetDateTime.ofInstant(clock.instant(), SEOUL)
        );
    }
}
