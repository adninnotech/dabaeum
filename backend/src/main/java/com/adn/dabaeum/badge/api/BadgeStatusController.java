package com.adn.dabaeum.badge.api;

import com.adn.dabaeum.accesslog.domain.AccessLogAction;
import com.adn.dabaeum.accesslog.web.PersonalDataAccess;
import com.adn.dabaeum.badge.application.BadgeStatusService;
import com.adn.dabaeum.badge.domain.LearningBadgeEvent;
import com.adn.dabaeum.badge.domain.LearningBadgeStatus;
import com.adn.dabaeum.common.api.ApiMeta;
import com.adn.dabaeum.common.api.ApiResponse;
import com.adn.dabaeum.common.api.PageMeta;
import com.adn.dabaeum.common.security.CurrentUserProvider;
import com.adn.dabaeum.common.web.RequestIdFilter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** NFT 학습배지 현황 (설계서 §11.4). 관리자 검색·폐기는 규칙표가 PLATFORM_ADMIN 으로 막는다. */
@RestController
@Profile({"local", "dev"})
@RequestMapping("/api/v1")
public class BadgeStatusController {

    private final BadgeStatusService service;
    private final LearningBadgeApiMapper mapper;
    private final CurrentUserProvider currentUserProvider;
    private final Clock clock;

    public BadgeStatusController(BadgeStatusService service, LearningBadgeApiMapper mapper,
        CurrentUserProvider currentUserProvider, Clock clock) {
        this.service = service;
        this.mapper = mapper;
        this.currentUserProvider = currentUserProvider;
        this.clock = clock;
    }

    public record BadgeEventResponse(UUID id, String type, Integer fromLevel, Integer toLevel, String reason,
        UUID actorId, Instant occurredAt) {

        static BadgeEventResponse from(LearningBadgeEvent event) {
            return new BadgeEventResponse(event.id(), event.type().name(), event.fromLevel(), event.toLevel(),
                event.reason(), event.actorId(), event.occurredAt());
        }
    }

    @GetMapping("/admin/badges")
    @PersonalDataAccess(action = AccessLogAction.READ)
    public LearningBadgePageResponse search(
        @RequestParam(required = false) UUID definitionId,
        @RequestParam(required = false) LearningBadgeStatus status,
        @RequestParam(required = false) Integer tierLevel,
        @RequestParam(required = false) String q,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        HttpServletRequest request
    ) {
        BadgeStatusService.Page result = service.search(definitionId, status, tierLevel, q, page, size);
        return new LearningBadgePageResponse(
            result.data().stream().map(mapper::toResponse).toList(),
            new PageMeta(result.page(), result.size(), result.totalElements(), result.totalPages()),
            meta(request));
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record RevokeRequest(String reason) {
    }

    @PostMapping("/admin/badges/{badgeId}/revoke")
    @PersonalDataAccess(action = AccessLogAction.UPDATE)
    public ApiResponse<LearningBadgeResponse> revoke(
        @PathVariable UUID badgeId, @RequestBody(required = false) RevokeRequest body, HttpServletRequest request
    ) {
        return new ApiResponse<>(mapper.toResponse(service.revoke(badgeId, body == null ? null : body.reason(),
            currentUserProvider.requireUserId())), meta(request));
    }

    @GetMapping("/badges/{badgeId}/history")
    public ApiResponse<List<BadgeEventResponse>> history(@PathVariable UUID badgeId, HttpServletRequest request) {
        return new ApiResponse<>(service.history(badgeId, currentUserProvider.requireContext()).stream()
            .map(BadgeEventResponse::from).toList(), meta(request));
    }

    private ApiMeta meta(HttpServletRequest request) {
        return new ApiMeta((String) request.getAttribute(RequestIdFilter.ATTRIBUTE_NAME),
            OffsetDateTime.ofInstant(clock.instant(), ZoneId.of("Asia/Seoul")));
    }
}
