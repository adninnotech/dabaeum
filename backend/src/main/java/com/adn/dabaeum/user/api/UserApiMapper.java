package com.adn.dabaeum.user.api;

import com.adn.dabaeum.common.api.ApiMeta;
import com.adn.dabaeum.common.api.ApiResponse;
import com.adn.dabaeum.common.api.PageMeta;
import com.adn.dabaeum.user.application.CreateUserCommand;
import com.adn.dabaeum.user.application.ChangeUserStatusCommand;
import com.adn.dabaeum.user.application.ListUsersQuery;
import com.adn.dabaeum.user.application.UpdateUserCommand;
import com.adn.dabaeum.user.application.UpdateMeCommand;
import com.adn.dabaeum.user.application.UserPage;
import com.adn.dabaeum.user.domain.User;
import com.adn.dabaeum.user.domain.UserStatus;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class UserApiMapper {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final Clock clock;

    public UserApiMapper(Clock clock) {
        this.clock = clock;
    }

    public CreateUserCommand toCommand(UserCreateRequest request) {
        return new CreateUserCommand(
            request.name(),
            request.email(),
            request.phone(),
            request.birthDate(),
            request.status()
        );
    }

    public ListUsersQuery toListQuery(
        int page,
        int size,
        String sort,
        UserStatus status
    ) {
        return new ListUsersQuery(page, size, sort, status);
    }

    public UpdateUserCommand toUpdateCommand(
        java.util.UUID userId,
        UserUpdateRequest request
    ) {
        return new UpdateUserCommand(
            userId,
            request.nameUpdate(),
            request.emailUpdate(),
            request.phoneUpdate(),
            request.birthDateUpdate(),
            request.careerUpdate(),
            request.introductionUpdate(),
            request.profileImageIdUpdate()
        );
    }

    public ChangeUserStatusCommand toChangeStatusCommand(
        java.util.UUID userId,
        UserStatusChangeRequest request
    ) {
        return new ChangeUserStatusCommand(userId, request.status());
    }

    public UpdateMeCommand toUpdateMeCommand(UserMeUpdateRequest request) {
        return new UpdateMeCommand(
            request.nameUpdate(),
            request.emailUpdate(),
            request.phoneUpdate(),
            request.birthDateUpdate(),
            request.careerUpdate(),
            request.introductionUpdate(),
            request.profileImageIdUpdate()
        );
    }

    public ApiResponse<UserResponse> toApiResponse(User user, String requestId) {
        return new ApiResponse<>(toResponse(user), apiMeta(requestId));
    }

    public UserResponse toResponse(User user) {
        return new UserResponse(
            user.id(),
            user.name(),
            user.email(),
            user.phone(),
            user.birthDate(),
            user.status(),
            user.withdrawnAt(),
            user.createdAt(),
            user.updatedAt(),
            user.career(),
            user.introduction(),
            user.profileImageId()
        );
    }

    public UserPageResponse toPageResponse(UserPage page, String requestId) {
        List<UserResponse> data = page.data()
            .stream()
            .map(this::toResponse)
            .toList();
        return new UserPageResponse(
            data,
            new PageMeta(
                page.page(),
                page.size(),
                page.totalElements(),
                page.totalPages()
            ),
            apiMeta(requestId)
        );
    }

    private ApiMeta apiMeta(String requestId) {
        return new ApiMeta(
            requestId,
            OffsetDateTime.ofInstant(clock.instant(), SEOUL)
        );
    }
}
