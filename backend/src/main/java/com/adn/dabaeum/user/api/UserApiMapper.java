package com.adn.dabaeum.user.api;

import com.adn.dabaeum.common.api.ApiMeta;
import com.adn.dabaeum.common.api.ApiResponse;
import com.adn.dabaeum.common.api.PageMeta;
import com.adn.dabaeum.common.api.KeywordQuery;
import com.adn.dabaeum.role.domain.RoleSummary;
import com.adn.dabaeum.role.domain.UserRole;
import com.adn.dabaeum.user.application.CreateUserCommand;
import com.adn.dabaeum.user.application.ChangeUserStatusCommand;
import com.adn.dabaeum.user.application.ListUsersQuery;
import com.adn.dabaeum.user.application.UpdateUserCommand;
import com.adn.dabaeum.user.application.UpdateMeCommand;
import com.adn.dabaeum.user.application.UserLastLoginFilter;
import com.adn.dabaeum.user.application.UserInterests;
import com.adn.dabaeum.user.application.UserPage;
import com.adn.dabaeum.user.application.UserView;
import com.adn.dabaeum.user.domain.Gender;
import com.adn.dabaeum.user.domain.User;
import com.adn.dabaeum.user.domain.UserStatus;
import java.time.Clock;
import java.time.LocalDate;
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
        UserStatus status,
        UserRole role,
        String q,
        StudentFilters filters
    ) {
        return new ListUsersQuery(
            page, size, sort, status, KeywordQuery.normalize(q), role,
            filters.gender(), filters.hasEnrollment(),
            filters.lastLogin(), filters.createdFrom(), filters.createdTo());
    }

    /** 관리자 학생 목록 화면의 서버 필터 묶음. 값 검증은 서비스가 한다. */
    public record StudentFilters(
        Gender gender,
        Boolean hasEnrollment,
        UserLastLoginFilter lastLogin,
        LocalDate createdFrom,
        LocalDate createdTo
    ) {
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
            request.profileImageIdUpdate(),
            request.genderUpdate()
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
            request.profileImageIdUpdate(),
            request.genderUpdate()
        );
    }

    /** 본인 조회·수정 전용. interests 를 받지 못하면 관심 분야 필드를 내리지 않는다. */
    public ApiResponse<UserResponse> toMeResponse(
        User user, List<RoleSummary> roles, UserInterests interests, String requestId
    ) {
        UserResponse response = toResponse(user, roles, false, null, null);
        return new ApiResponse<>(
            interests == null ? response : response.withInterests(interests.codes(), interests.agreed()),
            apiMeta(requestId));
    }

    /** 본인 조회(/users/me) 전용 — 관리자 메모는 절대 포함하지 않는다. */
    public ApiResponse<UserResponse> toApiResponse(UserView view, String requestId) {
        return toApiResponse(view, false, requestId);
    }

    /**
     * GET /users/{id} 처럼 관리자 전용 조회에서 includeAdminMemo=true 로 부른다.
     * enrollmentCount·courseCount 는 여기서 절대 채우지 않는다 — 목록(toPageResponse)만 채운다.
     * UserView 가 실수로 값을 들고 있어도(get·getMe 경로는 채우지 않는다) 단건 응답에는
     * 새지 않도록 always null 로 고정한다.
     */
    public ApiResponse<UserResponse> toApiResponse(
        UserView view,
        boolean includeAdminMemo,
        String requestId
    ) {
        return new ApiResponse<>(
            toResponse(view.user(), view.roles(), includeAdminMemo, null, null),
            apiMeta(requestId)
        );
    }

    /**
     * 변경 API 는 도메인 User 만 돌려받으므로 역할을 따로 받아 붙인다.
     * roles 없는 User JSON 을 만들 길이 없도록 User 단독 변환은 두지 않는다.
     * includeAdminMemo 는 관리자 전용 엔드포인트에서만 true — 본인 조회 계열은 false 로 고정한다.
     */
    public ApiResponse<UserResponse> toApiResponse(
        User user,
        List<RoleSummary> roles,
        boolean includeAdminMemo,
        String requestId
    ) {
        return new ApiResponse<>(toResponse(user, roles, includeAdminMemo), apiMeta(requestId));
    }

    /**
     * 관리자 메모는 당사자가 보면 사고다. includeAdminMemo 가 false 면 값이 있어도 null 로 내린다 —
     * UserResponse.adminMemo 의 @JsonInclude(NON_NULL) 이 null 을 JSON 에서 아예 지운다.
     * enrollmentCount·courseCount 는 이 오버로드로는 절대 채워지지 않는다(생성·수정 응답 전용) —
     * 채우려면 아래 5-인자 오버로드를 쓴다.
     */
    public UserResponse toResponse(User user, List<RoleSummary> roles, boolean includeAdminMemo) {
        return toResponse(user, roles, includeAdminMemo, null, null);
    }

    /**
     * 목록 응답 전용. enrollmentCount·courseCount 는 UserResponse 의 @JsonInclude(NON_NULL) 이
     * null 이면 JSON 에서 지운다 — get·getMe·생성·수정 경로는 항상 null 을 넘긴다.
     */
    public UserResponse toResponse(
        User user,
        List<RoleSummary> roles,
        boolean includeAdminMemo,
        Long enrollmentCount,
        Long courseCount
    ) {
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
            user.profileImageId(),
            user.gender(),
            user.lastLoginAt(),
            user.emailNotificationEnabled(),
            user.smsNotificationEnabled(),
            includeAdminMemo ? user.adminMemo() : null,
            roles == null ? List.of() : roles,
            enrollmentCount,
            courseCount,
            null,
            null
        );
    }

    /** 목록은 관리자 전용 화면(B-4)에서만 쓰인다. */
    public UserPageResponse toPageResponse(UserPage page, String requestId) {
        List<UserResponse> data = page.data()
            .stream()
            .map(view -> toResponse(
                view.user(), view.roles(), true, view.enrollmentCount(), view.courseCount()))
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
