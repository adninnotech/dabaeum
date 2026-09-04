package com.adn.dabaeum.role.api;

import com.adn.dabaeum.common.api.ApiMeta;
import com.adn.dabaeum.common.api.ApiResponse;
import com.adn.dabaeum.role.application.AssignRoleCommand;
import com.adn.dabaeum.role.domain.UserRoleAssignment;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class RoleApiMapper {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final Clock clock;

    public RoleApiMapper(Clock clock) {
        this.clock = clock;
    }

    public AssignRoleCommand toCommand(
        UUID userId,
        RoleAssignmentRequest request
    ) {
        return new AssignRoleCommand(
            userId,
            request.role(),
            request.institutionId()
        );
    }

    public ApiResponse<List<RoleResponse>> toListResponse(
        List<UserRoleAssignment> assignments,
        String requestId
    ) {
        return new ApiResponse<>(
            assignments.stream().map(this::toResponse).toList(),
            apiMeta(requestId)
        );
    }

    public ApiResponse<RoleResponse> toResponse(
        UserRoleAssignment assignment,
        String requestId
    ) {
        return new ApiResponse<>(toResponse(assignment), apiMeta(requestId));
    }

    public RoleResponse toResponse(UserRoleAssignment assignment) {
        return new RoleResponse(
            assignment.id(),
            assignment.role(),
            assignment.institutionId(),
            assignment.createdAt()
        );
    }

    private ApiMeta apiMeta(String requestId) {
        return new ApiMeta(
            requestId,
            OffsetDateTime.ofInstant(clock.instant(), SEOUL)
        );
    }
}
