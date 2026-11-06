package com.adn.dabaeum.badge.api;

import com.adn.dabaeum.badge.application.IssueLearningBadgeCommand;
import com.adn.dabaeum.badge.application.LearningBadgePage;
import com.adn.dabaeum.badge.application.ListUserBadgesQuery;
import com.adn.dabaeum.badge.domain.BadgeCriteria;
import com.adn.dabaeum.classification.application.CourseClassificationService;
import com.adn.dabaeum.badge.domain.BadgeRule;
import com.adn.dabaeum.badge.domain.LearningBadge;
import com.adn.dabaeum.badge.domain.LearningBadgeEvent;
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
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class LearningBadgeApiMapper {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final Set<String> ALLOWED_SORTS = Set.of(
        "createdAt,asc", "createdAt,desc", "issuedAt,asc", "issuedAt,desc",
        "updatedAt,asc", "updatedAt,desc");

    private final Clock clock;
    private final String issuerName;
    private final ObjectProvider<CourseClassificationService> classifications;

    public LearningBadgeApiMapper(Clock clock, @Value("${dabaeum.badge.issuer-name:다배움}") String issuerName,
        ObjectProvider<CourseClassificationService> classifications) {
        this.clock = clock;
        this.issuerName = issuerName;
        this.classifications = classifications;
    }

    public IssueLearningBadgeCommand toIssueCommand(
        UUID credentialId,
        BadgeIssueRequest request,
        String idempotencyKey,
        AuthenticatedUserContext actor
    ) {
        if (request == null || request.definitionId() == null) {
            throw validation(List.of("definitionId"));
        }
        return new IssueLearningBadgeCommand(
            credentialId, request.definitionId(), request.courseId(),
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
        LearningBadge.Details details = badge.details();
        BadgeRule rule = details == null ? null : details.rule();
        Integer tierLevel = details == null ? null : details.tierLevel();
        Optional<BadgeRule.TierLevel> tier = rule instanceof BadgeRule.Tiered tiered && tierLevel != null
            ? tiered.level(tierLevel) : Optional.empty();
        UUID image = tier.map(BadgeRule.TierLevel::imageFileId).orElse(null);
        LearningBadgeEvent last = details == null ? null : details.lastEvent();
        return new LearningBadgeResponse(
            badge.id(), badge.userId(), badge.courseId(), badge.credentialId(),
            badge.badgeType(), badge.badgeName(), badge.status(), badge.nftTokenId(),
            badge.issuedAt(), badge.revokedAt(), badge.createdAt(), badge.updatedAt(),
            badge.definitionId(), badge.definitionCode(),
            BadgeDefinitionResponse.imageUrl(image != null ? image : badge.imageFileId()),
            issuerName,
            details == null ? null : details.recipientName(),
            tierLevel,
            tier.map(BadgeRule.TierLevel::name).orElse(null),
            BadgeCriteria.describe(rule, this::classificationPath),
            details == null ? null : details.expiresAt(),
            last == null ? null
                : new LearningBadgeResponse.LastEvent(last.type().name(), last.reason(), last.occurredAt()));
    }

    private List<String> classificationPath(String code) {
        CourseClassificationService service = classifications.getIfAvailable();
        return service == null ? List.of() : service.pathOf(code);
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
            HttpStatus.UNPROCESSABLE_CONTENT,
            ApiErrorCode.VALIDATION_FAILED,
            "Request validation failed",
            fields);
    }

    private ApiMeta meta(String requestId) {
        return new ApiMeta(requestId, OffsetDateTime.ofInstant(clock.instant(), SEOUL));
    }
}
