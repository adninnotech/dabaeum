package com.adn.dabaeum.badge.application;

import com.adn.dabaeum.badge.domain.BadgeAwardRepository;
import com.adn.dabaeum.badge.domain.LearningBadge;
import com.adn.dabaeum.badge.domain.LearningBadgeEvent;
import com.adn.dabaeum.badge.domain.LearningBadgeEventRepository;
import com.adn.dabaeum.badge.domain.LearningBadgeRepository;
import com.adn.dabaeum.badge.domain.LearningBadgeRepository.BadgeSearch;
import com.adn.dabaeum.badge.domain.LearningBadgeStatus;
import com.adn.dabaeum.common.api.ApiErrorCode;
import com.adn.dabaeum.common.api.ApiException;
import com.adn.dabaeum.common.api.KeywordQuery;
import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** NFT 학습배지 현황(설계서 §11.4): 관리자 검색과 배지 변동 이력. */
@Service
@Profile({"local", "dev"})
public class BadgeStatusService {

    private static final int MAX_SIZE = 100;
    private static final int MAX_TIER = 10;

    private final LearningBadgeRepository badges;
    private final LearningBadgeEventRepository events;
    private final LearningBadgeApplicationService badgeService;
    private final BadgeAwardRepository awards;
    private final Clock clock;

    public BadgeStatusService(LearningBadgeRepository badges, LearningBadgeEventRepository events,
        LearningBadgeApplicationService badgeService, BadgeAwardRepository awards, Clock clock) {
        this.badges = Objects.requireNonNull(badges);
        this.events = Objects.requireNonNull(events);
        this.badgeService = Objects.requireNonNull(badgeService);
        this.awards = Objects.requireNonNull(awards);
        this.clock = Objects.requireNonNull(clock);
    }

    public record Page(List<LearningBadge> data, int page, int size, long totalElements, int totalPages) {

        public Page {
            data = List.copyOf(data);
        }
    }

    @Transactional(readOnly = true)
    public Page search(UUID definitionId, LearningBadgeStatus status, Integer tierLevel, String q, int page,
        int size) {
        if (page < 0 || size < 1 || size > MAX_SIZE || page > Integer.MAX_VALUE / size) {
            throw badRequest("page", "size");
        }
        if (tierLevel != null && (tierLevel < 1 || tierLevel > MAX_TIER)) {
            throw badRequest("tierLevel");
        }
        BadgeSearch criteria = new BadgeSearch(definitionId, status, tierLevel, KeywordQuery.normalize(q), size,
            page * size);
        long total = badges.count(criteria);
        return new Page(badges.search(criteria), page, size, total, (int) ((total + size - 1) / size));
    }

    /** 배지 조회 권한(본인·관리 권한자)을 그대로 따른다. 최근 것부터. */
    @Transactional(readOnly = true)
    public List<LearningBadgeEvent> history(UUID badgeId, AuthenticatedUserContext actor) {
        badgeService.get(badgeId, actor);
        return events.findByBadgeId(badgeId);
    }

    /**
     * 관리자 폐기(플랫폼 관리자, 규칙표가 막는다). 사유와 행위자를 이력에 남기고, 이렇게 폐기한 배지는
     * 조건을 다시 채워도 자동으로 되살리지 않는다. 이미 폐기된 배지는 409.
     */
    @Transactional
    public LearningBadge revoke(UUID badgeId, String reason, UUID actorId) {
        String trimmed = reason == null ? "" : reason.trim();
        if (trimmed.isEmpty() || trimmed.length() > LearningBadgeEvent.MAX_REASON) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, ApiErrorCode.VALIDATION_FAILED,
                "Request validation failed", List.of("reason"));
        }
        LearningBadge badge = badges.findById(badgeId).orElseThrow(() -> new ApiException(
            HttpStatus.NOT_FOUND, ApiErrorCode.BADGE_NOT_FOUND, "Badge not found"));
        Instant now = clock.instant();
        if (badge.status() != LearningBadgeStatus.ISSUED || !awards.revoke(badgeId, now)) {
            throw new ApiException(HttpStatus.CONFLICT, ApiErrorCode.BADGE_CONFLICT, "Badge is not issued");
        }
        Integer level = badge.details() == null ? null : badge.details().tierLevel();
        events.save(LearningBadgeEvent.of(badgeId, LearningBadgeEvent.Type.REVOKED, level, null, trimmed, actorId, now));
        return badges.findById(badgeId).orElseThrow();
    }

    private static ApiException badRequest(String... fields) {
        return new ApiException(HttpStatus.BAD_REQUEST, ApiErrorCode.BAD_REQUEST, "Request parameter is invalid",
            List.of(fields));
    }
}
