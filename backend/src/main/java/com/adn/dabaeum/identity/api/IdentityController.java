package com.adn.dabaeum.identity.api;

import com.adn.dabaeum.accesslog.domain.AccessLogAction;
import com.adn.dabaeum.accesslog.web.PersonalDataAccess;
import com.adn.dabaeum.common.api.ApiResponse;
import com.adn.dabaeum.common.security.CurrentUserProvider;
import com.adn.dabaeum.common.web.RequestIdFilter;
import com.adn.dabaeum.identity.application.IdentityApplicationService;
import com.adn.dabaeum.identity.domain.UserIdentity;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
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
public class IdentityController {

    private final IdentityApplicationService service;
    private final IdentityApiMapper mapper;
    private final CurrentUserProvider currentUserProvider;

    public IdentityController(
        IdentityApplicationService service,
        IdentityApiMapper mapper,
        CurrentUserProvider currentUserProvider
    ) {
        this.service = service;
        this.mapper = mapper;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping("/me/identities")
    public ApiResponse<List<IdentityResponse>> listMine(HttpServletRequest servletRequest) {
        return mapper.toListResponse(
            service.list(currentUserProvider.requireUserId()),
            requestId(servletRequest)
        );
    }

    @GetMapping("/{userId}/identities")
    @PersonalDataAccess(action = AccessLogAction.READ)
    public ApiResponse<List<IdentityResponse>> list(
        @PathVariable UUID userId,
        HttpServletRequest servletRequest
    ) {
        return mapper.toListResponse(
            service.list(userId),
            requestId(servletRequest)
        );
    }

    @PostMapping("/{userId}/identities")
    @PersonalDataAccess(action = AccessLogAction.UPDATE)
    public ResponseEntity<ApiResponse<IdentityResponse>> link(
        @PathVariable UUID userId,
        @Valid @RequestBody IdentityLinkRequest request,
        HttpServletRequest servletRequest
    ) {
        UserIdentity identity = service.link(mapper.toCommand(userId, request));
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{identityId}")
            .buildAndExpand(identity.id())
            .toUri();
        return ResponseEntity.created(location)
            .body(mapper.toResponse(identity, requestId(servletRequest)));
    }

    @DeleteMapping("/{userId}/identities/{identityId}")
    @PersonalDataAccess(action = AccessLogAction.DELETE)
    public ApiResponse<IdentityResponse> unlink(
        @PathVariable UUID userId,
        @PathVariable UUID identityId,
        HttpServletRequest servletRequest
    ) {
        return mapper.toResponse(
            service.unlink(userId, identityId),
            requestId(servletRequest)
        );
    }

    private String requestId(HttpServletRequest request) {
        return (String) request.getAttribute(RequestIdFilter.ATTRIBUTE_NAME);
    }
}
