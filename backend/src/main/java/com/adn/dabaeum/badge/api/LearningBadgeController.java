package com.adn.dabaeum.badge.api;

import com.adn.dabaeum.badge.application.LearningBadgeApplicationService;
import com.adn.dabaeum.badge.application.LearningBadgePage;
import com.adn.dabaeum.common.api.ApiErrorCode;
import com.adn.dabaeum.common.api.ApiException;
import com.adn.dabaeum.common.api.ApiResponse;
import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import com.adn.dabaeum.common.security.CurrentUserProvider;
import com.adn.dabaeum.common.web.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile({"local", "dev"})
@RequestMapping("/api/v1")
public class LearningBadgeController {

    private final LearningBadgeApplicationService service;
    private final LearningBadgeApiMapper mapper;
    private final CurrentUserProvider currentUserProvider;

    public LearningBadgeController(
        LearningBadgeApplicationService service,
        LearningBadgeApiMapper mapper,
        CurrentUserProvider currentUserProvider
    ) {
        this.service = service;
        this.mapper = mapper;
        this.currentUserProvider = currentUserProvider;
    }

    @PostMapping("/credentials/{credentialId}/badges")
    public ResponseEntity<ApiResponse<LearningBadgeResponse>> issue(
        @PathVariable UUID credentialId,
        @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
        @RequestBody(required = false) BadgeIssueRequest request,
        HttpServletRequest servletRequest
    ) {
        requireIdempotencyKey(idempotencyKey);
        AuthenticatedUserContext actor = currentUserProvider.requireContext();
        var badge = service.issue(
            mapper.toIssueCommand(credentialId, request, idempotencyKey, actor));
        return ResponseEntity.status(HttpStatus.ACCEPTED)
            .location(URI.create("/api/v1/badges/" + badge.id()))
            .body(mapper.toApiResponse(badge, requestId(servletRequest)));
    }

    @GetMapping("/badges/{badgeId}")
    public ApiResponse<LearningBadgeResponse> get(
        @PathVariable UUID badgeId,
        HttpServletRequest servletRequest
    ) {
        return mapper.toApiResponse(
            service.get(badgeId, currentUserProvider.requireContext()),
            requestId(servletRequest));
    }

    @GetMapping("/users/{userId}/badges")
    public LearningBadgePageResponse listByUser(
        @PathVariable UUID userId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(defaultValue = "createdAt,desc") String sort,
        HttpServletRequest servletRequest
    ) {
        LearningBadgePage result = service.listByUser(mapper.toListQuery(
            userId, page, size, sort, currentUserProvider.requireContext()));
        return mapper.toPageResponse(result, requestId(servletRequest));
    }

    private void requireIdempotencyKey(String value) {
        if (value == null || value.trim().length() < 8 || value.trim().length() > 128) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                ApiErrorCode.BAD_REQUEST,
                "Idempotency-Key header is invalid",
                List.of("Idempotency-Key"));
        }
    }

    private String requestId(HttpServletRequest request) {
        return (String) request.getAttribute(RequestIdFilter.ATTRIBUTE_NAME);
    }
}
