package com.adn.dabaeum.user.api;

import com.adn.dabaeum.accesslog.domain.AccessLogAction;
import com.adn.dabaeum.accesslog.web.PersonalDataAccess;
import com.adn.dabaeum.common.api.ApiErrorCode;
import com.adn.dabaeum.common.api.ApiException;
import com.adn.dabaeum.common.api.ApiResponse;
import com.adn.dabaeum.common.security.CurrentUserProvider;
import com.adn.dabaeum.common.web.RequestIdFilter;
import com.adn.dabaeum.role.domain.UserRole;
import com.adn.dabaeum.user.application.UpdateMeCommand;
import com.adn.dabaeum.user.application.UserApplicationService;
import com.adn.dabaeum.user.application.UserLastLoginFilter;
import com.adn.dabaeum.user.application.UserPage;
import com.adn.dabaeum.user.application.UserView;
import com.adn.dabaeum.user.domain.Gender;
import com.adn.dabaeum.user.domain.User;
import com.adn.dabaeum.user.domain.UserStatus;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserApplicationService service;
    private final UserApiMapper mapper;
    private final CurrentUserProvider currentUserProvider;

    public UserController(
        UserApplicationService service,
        UserApiMapper mapper,
        CurrentUserProvider currentUserProvider
    ) {
        this.service = service;
        this.mapper = mapper;
        this.currentUserProvider = currentUserProvider;
    }

    @PostMapping
    @PersonalDataAccess(action = AccessLogAction.UPDATE, targetField = "id")
    public ResponseEntity<ApiResponse<UserResponse>> create(
        @Valid @RequestBody UserCreateRequest request,
        HttpServletRequest servletRequest
    ) {
        User user = service.create(mapper.toCommand(request));
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{userId}")
            .buildAndExpand(user.id())
            .toUri();
        return ResponseEntity.created(location)
            // 방금 만든 사용자는 역할이 아직 없다. 빈 목록이 확실하니 다시 묻지 않는다.
            // 관리자 전용 API 이니 includeAdminMemo=true (생성 직후라 항상 null 이지만 규칙은 지킨다).
            .body(mapper.toApiResponse(user, List.of(), true, requestId(servletRequest)));
    }

    @GetMapping
    @PersonalDataAccess(action = AccessLogAction.READ, targetField = "id")
    public UserPageResponse list(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(defaultValue = "createdAt,desc") String sort,
        @RequestParam(required = false) UserStatus status,
        @RequestParam(required = false) UserRole role,
        @RequestParam(required = false) String q,
        @RequestParam(required = false) Gender gender,
        @RequestParam(required = false) Boolean hasEnrollment,
        @RequestParam(required = false) UserLastLoginFilter lastLogin,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdFrom,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdTo,
        HttpServletRequest servletRequest
    ) {
        UserPage result = service.list(mapper.toListQuery(
            page,
            size,
            sort,
            status,
            role,
            q,
            new UserApiMapper.StudentFilters(
                gender, hasEnrollment, lastLogin, createdFrom, createdTo)
        ));
        return mapper.toPageResponse(result, requestId(servletRequest));
    }

    @GetMapping("/me")
    public ApiResponse<UserResponse> me(HttpServletRequest servletRequest) {
        UUID currentUserId = currentUserProvider.requireUserId();
        UserView view = service.getMe(currentUserId);
        return mapper.toMeResponse(
            view.user(), view.roles(), service.interestsOf(currentUserId), requestId(servletRequest));
    }

    @PutMapping("/me")
    public ApiResponse<UserResponse> updateMe(
        @Valid @RequestBody UserMeUpdateRequest request,
        HttpServletRequest servletRequest
    ) {
        UUID currentUserId = currentUserProvider.requireUserId();
        UpdateMeCommand command = mapper.toUpdateMeCommand(request);
        User updated = request.interestCodes() == null
            ? service.updateMe(currentUserId, command)
            : service.updateMeWithInterests(
                currentUserId, command, request.interestCodes(), request.agreedToInterest());
        return mapper.toMeResponse(
            updated, service.rolesOf(currentUserId), service.interestsOf(currentUserId), requestId(servletRequest));
    }

    @PostMapping("/me/withdraw")
    public ApiResponse<UserResponse> withdrawMe(
        @RequestBody(required = false) WithdrawRequest request,
        HttpServletRequest servletRequest
    ) {
        UUID currentUserId = currentUserProvider.requireUserId();
        return mapper.toApiResponse(
            service.withdrawSelf(currentUserId),
            service.rolesOf(currentUserId),
            false,
            requestId(servletRequest)
        );
    }

    @PutMapping("/me/notification-preferences")
    public ApiResponse<UserResponse> updateMyNotificationPreferences(
        @RequestBody NotificationPreferenceUpdateRequest request,
        HttpServletRequest servletRequest
    ) {
        UUID currentUserId = currentUserProvider.requireUserId();
        return mapper.toApiResponse(
            service.updateNotificationPreferences(
                currentUserId,
                request.emailNotificationEnabled(),
                request.smsNotificationEnabled()
            ),
            service.rolesOf(currentUserId),
            false,
            requestId(servletRequest)
        );
    }

    @GetMapping("/{userId}")
    @PersonalDataAccess(action = AccessLogAction.READ, targetField = "id")
    public ApiResponse<UserResponse> get(
        @PathVariable UUID userId,
        HttpServletRequest servletRequest
    ) {
        return mapper.toApiResponse(
            service.get(userId),
            true,
            requestId(servletRequest)
        );
    }

    @PutMapping("/{userId}")
    @PersonalDataAccess(action = AccessLogAction.UPDATE, targetField = "id")
    public ApiResponse<UserResponse> update(
        @PathVariable UUID userId,
        @Valid @RequestBody UserUpdateRequest request,
        HttpServletRequest servletRequest
    ) {
        return mapper.toApiResponse(
            service.updateProfile(mapper.toUpdateCommand(userId, request)),
            service.rolesOf(userId),
            true,
            requestId(servletRequest)
        );
    }

    @PatchMapping("/{userId}/status")
    @PersonalDataAccess(action = AccessLogAction.UPDATE, targetField = "id")
    public ApiResponse<UserResponse> changeStatus(
        @PathVariable UUID userId,
        @Valid @RequestBody UserStatusChangeRequest request,
        HttpServletRequest servletRequest
    ) {
        return mapper.toApiResponse(
            service.changeStatus(
                mapper.toChangeStatusCommand(userId, request)
            ),
            service.rolesOf(userId),
            true,
            requestId(servletRequest)
        );
    }

    /**
     * B-4 회원 상세의 연필 아이콘. 사이트 전역 관리자 메모 — 강사 메모(InstructorMemoController)와
     * 달리 기관 구분이 없다. null 은 삭제, 빈 문자열은 실수일 가능성이 높아 422 로 막는다.
     */
    @PatchMapping("/{userId}/memo")
    @PersonalDataAccess(action = AccessLogAction.UPDATE, targetField = "id")
    public ApiResponse<UserResponse> updateMemo(
        @PathVariable UUID userId,
        @RequestBody(required = false) UserMemoUpdateRequest request,
        HttpServletRequest servletRequest
    ) {
        String memo = request == null || request.memo() == null
            ? null : request.memo().trim();
        if (memo != null && (memo.isEmpty() || memo.length() > 1000)) {
            throw new ApiException(
                HttpStatus.UNPROCESSABLE_CONTENT, ApiErrorCode.VALIDATION_FAILED,
                "Request validation failed", List.of("memo"));
        }
        return mapper.toApiResponse(
            service.updateAdminMemo(userId, memo),
            service.rolesOf(userId),
            true,
            requestId(servletRequest)
        );
    }

    private String requestId(HttpServletRequest request) {
        return (String) request.getAttribute(RequestIdFilter.ATTRIBUTE_NAME);
    }
}
