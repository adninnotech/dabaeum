package com.adn.dabaeum.user.api;

import com.adn.dabaeum.common.api.ApiResponse;
import com.adn.dabaeum.common.security.CurrentUserProvider;
import com.adn.dabaeum.common.web.RequestIdFilter;
import com.adn.dabaeum.user.application.UserApplicationService;
import com.adn.dabaeum.user.application.UserPage;
import com.adn.dabaeum.user.domain.User;
import com.adn.dabaeum.user.domain.UserStatus;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
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
            .body(mapper.toApiResponse(user, requestId(servletRequest)));
    }

    @GetMapping
    public UserPageResponse list(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(defaultValue = "createdAt,desc") String sort,
        @RequestParam(required = false) UserStatus status,
        HttpServletRequest servletRequest
    ) {
        UserPage result = service.list(mapper.toListQuery(
            page,
            size,
            sort,
            status
        ));
        return mapper.toPageResponse(result, requestId(servletRequest));
    }

    @GetMapping("/me")
    public ApiResponse<UserResponse> me(HttpServletRequest servletRequest) {
        UUID currentUserId = currentUserProvider.requireUserId();
        return mapper.toApiResponse(
            service.getMe(currentUserId),
            requestId(servletRequest)
        );
    }

    @PutMapping("/me")
    public ApiResponse<UserResponse> updateMe(
        @Valid @RequestBody UserMeUpdateRequest request,
        HttpServletRequest servletRequest
    ) {
        UUID currentUserId = currentUserProvider.requireUserId();
        return mapper.toApiResponse(
            service.updateMe(
                currentUserId,
                mapper.toUpdateMeCommand(request)
            ),
            requestId(servletRequest)
        );
    }

    @GetMapping("/{userId}")
    public ApiResponse<UserResponse> get(
        @PathVariable UUID userId,
        HttpServletRequest servletRequest
    ) {
        return mapper.toApiResponse(
            service.get(userId),
            requestId(servletRequest)
        );
    }

    @PutMapping("/{userId}")
    public ApiResponse<UserResponse> update(
        @PathVariable UUID userId,
        @Valid @RequestBody UserUpdateRequest request,
        HttpServletRequest servletRequest
    ) {
        return mapper.toApiResponse(
            service.updateProfile(mapper.toUpdateCommand(userId, request)),
            requestId(servletRequest)
        );
    }

    @PatchMapping("/{userId}/status")
    public ApiResponse<UserResponse> changeStatus(
        @PathVariable UUID userId,
        @Valid @RequestBody UserStatusChangeRequest request,
        HttpServletRequest servletRequest
    ) {
        return mapper.toApiResponse(
            service.changeStatus(
                mapper.toChangeStatusCommand(userId, request)
            ),
            requestId(servletRequest)
        );
    }

    private String requestId(HttpServletRequest request) {
        return (String) request.getAttribute(RequestIdFilter.ATTRIBUTE_NAME);
    }
}
