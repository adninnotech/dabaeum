package com.adn.dabaeum.role.api;

import com.adn.dabaeum.authorization.application.AuthorizationPolicy;
import com.adn.dabaeum.common.api.ApiResponse;
import com.adn.dabaeum.common.web.RequestIdFilter;
import com.adn.dabaeum.role.application.RoleApplicationService;
import com.adn.dabaeum.role.domain.UserRoleAssignment;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/users")
public class RoleController {

    private final RoleApplicationService service;
    private final RoleApiMapper mapper;
    private final AuthorizationPolicy policy;

    public RoleController(
        RoleApplicationService service,
        RoleApiMapper mapper,
        AuthorizationPolicy policy
    ) {
        this.service = service;
        this.mapper = mapper;
        this.policy = policy;
    }

    @GetMapping("/{userId}/roles")
    public ApiResponse<List<RoleResponse>> list(
        @PathVariable UUID userId,
        HttpServletRequest servletRequest
    ) {
        authorize();
        return mapper.toListResponse(
            service.list(userId),
            requestId(servletRequest)
        );
    }

    @PostMapping("/{userId}/roles")
    public ResponseEntity<ApiResponse<RoleResponse>> assign(
        @PathVariable UUID userId,
        @Valid @RequestBody RoleAssignmentRequest request,
        HttpServletRequest servletRequest
    ) {
        authorize();
        UserRoleAssignment assignment = service.assign(
            mapper.toCommand(userId, request)
        );
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{roleId}")
            .buildAndExpand(assignment.id())
            .toUri();
        return ResponseEntity.created(location)
            .body(mapper.toResponse(assignment, requestId(servletRequest)));
    }

    @DeleteMapping("/{userId}/roles/{roleId}")
    public ApiResponse<RoleResponse> revoke(
        @PathVariable UUID userId,
        @PathVariable UUID roleId,
        HttpServletRequest servletRequest
    ) {
        authorize();
        return mapper.toResponse(
            service.revoke(userId, roleId),
            requestId(servletRequest)
        );
    }

    private void authorize() {
        policy.requirePlatformAdmin(
            SecurityContextHolder.getContext().getAuthentication()
        );
    }

    private String requestId(HttpServletRequest request) {
        return (String) request.getAttribute(RequestIdFilter.ATTRIBUTE_NAME);
    }
}
