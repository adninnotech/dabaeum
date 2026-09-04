package com.adn.dabaeum.badge.api;

import com.adn.dabaeum.badge.application.IssueLearningBadgeCommand;
import com.adn.dabaeum.badge.application.LearningBadgePage;
import com.adn.dabaeum.badge.application.ListUserBadgesQuery;
import com.adn.dabaeum.badge.domain.LearningBadge;
import com.adn.dabaeum.common.api.ApiErrorCode;
import com.adn.dabaeum.common.api.ApiException;
import com.adn.dabaeum.common.api.ApiMeta;
import com.adn.dabaeum.common.api.ApiResponse;
import com.adn.dabaeum.common.api.PageMeta;
import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class LearningBadgeApiMapper {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final Set<String> ALLOWED_SORTS = Set.of(
        "createdAt,asc", "createdAt,desc", "issuedAt,asc", "issuedAt,desc",
        "updatedAt,asc", "updatedAt,desc");

    private final Clock clock;

    public LearningBadgeApiMapper(Clock clock) {
        this.clock = clock;
    }

    public IssueLearningBadgeCommand toIssueCommand(
        UUID credentialId,
        BadgeIssueRequest request,
        String idempotencyKey,
        AuthenticatedUserContext actor
    ) {
        if (request == null) {
            throw validation(List.of("badgeType", "badgeName"));
        }
        String badgeType = trimmedOrNull(request.badgeType());
        String badgeName = trimmedOrNull(request.badgeName());
        List<String> invalid = new java.util.ArrayList<>();
        if (badgeType == null || badgeType.length() > 50) {
            invalid.add("badgeType");
        }
        if (badgeName == null || badgeName.length() > 200) {
            invalid.add("badgeName");
        }
        if (!invalid.isEmpty()) {
            throw validation(invalid);
        }
        return new IssueLearningBadgeCommand(
            credentialId, badgeType, badgeName, request.courseId(),
            idempotencyKey, actor, clock.instant());
    }

    public ListUserBadgesQuery toListQuery(
        UUID userId,
        int page,
        int size,
        String sort,
        AuthenticatedUserContext actor
    ) {
        if (page < 0 || size < 1 || size > 100 || sort == null || sort.isBlank()
            || page > Integer.MAX_VALUE / size
            || !ALLOWED_SORTS.contains(sort.trim())) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                ApiErrorCode.BAD_REQUEST,
                "Request parameter is invalid",
                List.of("page", "size", "sort"));
        }
        return new ListUserBadgesQuery(userId, page, size, sort.trim(), actor);
    }

    public ApiResponse<LearningBadgeResponse> toApiResponse(
        LearningBadge badge, String requestId
    ) {
        return new ApiResponse<>(toResponse(badge), meta(requestId));
    }

    public LearningBadgeResponse toResponse(LearningBadge badge) {
        return new LearningBadgeResponse(
            badge.id(), badge.userId(), badge.courseId(), badge.credentialId(),
            badge.badgeType(), badge.badgeName(), badge.status(), badge.nftTokenId(),
            badge.issuedAt(), badge.revokedAt(), badge.createdAt(), badge.updatedAt());
    }

    public LearningBadgePageResponse toPageResponse(
        LearningBadgePage page, String requestId
    ) {
        return new LearningBadgePageResponse(
            page.data().stream().map(this::toResponse).toList(),
            new PageMeta(page.page(), page.size(), page.totalElements(), page.totalPages()),
            meta(requestId));
    }

    private String trimmedOrNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }

    private ApiException validation(List<String> fields) {
        return new ApiException(
            HttpStatus.UNPROCESSABLE_ENTITY,
            ApiErrorCode.VALIDATION_FAILED,
            "Request validation failed",
            fields);
    }

    private ApiMeta meta(String requestId) {
        return new ApiMeta(requestId, OffsetDateTime.ofInstant(clock.instant(), SEOUL));
    }
}
