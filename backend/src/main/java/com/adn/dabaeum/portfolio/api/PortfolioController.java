package com.adn.dabaeum.portfolio.api;

import com.adn.dabaeum.badge.domain.BadgeRule;
import com.adn.dabaeum.common.api.ApiMeta;
import com.adn.dabaeum.common.api.ApiResponse;
import com.adn.dabaeum.common.api.DeletionResponse;
import com.adn.dabaeum.common.security.CurrentUserProvider;
import com.adn.dabaeum.common.web.RequestIdFilter;
import com.adn.dabaeum.portfolio.application.PortfolioService;
import com.adn.dabaeum.portfolio.domain.PortfolioRecords;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 학습포트폴리오 (설계서 §6). 본인 경로는 로그인, 공개 경로는 무인증. */
@RestController
@Profile({"local", "dev"})
@RequestMapping("/api/v1")
public class PortfolioController {

    private final PortfolioService service;
    private final CurrentUserProvider currentUserProvider;
    private final Clock clock;

    public PortfolioController(PortfolioService service, CurrentUserProvider currentUserProvider, Clock clock) {
        this.service = service;
        this.currentUserProvider = currentUserProvider;
        this.clock = clock;
    }

    public record CompletionItem(UUID courseId, String title, String institutionName, Instant completedAt,
        String classificationCode, UUID credentialId) {
    }

    public record CredentialItem(UUID id, String credentialNo, String courseTitle, String institutionName,
        Instant completedAt, Instant issuedAt, String status, String verifyPath) {
    }

    public record BadgeItem(UUID id, String definitionCode, String name, String imageUrl, Instant issuedAt,
        Integer tierLevel, String tierName) {
    }

    /** 사용 중인 배지 정의별 보유 여부. 보유하지 않았으면 badge 는 null. */
    public record CatalogItem(UUID definitionId, String code, String name, String description, String imageUrl,
        String criteria, boolean owned, BadgeItem badge, List<TierItem> levels) {
    }

    /** 단계 배지의 단계표. 단계 배지가 아니면 levels 는 null. */
    public record TierItem(int level, String name, int minCount, String imageUrl) {
    }

    public record PortfolioResponse(String name, Instant joinedAt, PortfolioService.Summary summary,
        List<PortfolioService.CategoryCount> categoryDistribution, List<CompletionItem> completions,
        List<CredentialItem> credentials, List<BadgeItem> badges, List<CatalogItem> badgeCatalog) {
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record ShareRequest(List<UUID> credentialIds, List<UUID> badgeIds, Integer expiresInDays) {
    }

    public record ShareCreated(UUID shareId, String token, Instant expiresAt) {

        @Override
        public String toString() {
            return "ShareCreated[shareId=" + shareId + ", token=***]";
        }
    }

    public record ShareItem(UUID id, int credentialCount, int badgeCount, Instant expiresAt, Instant revokedAt,
        int viewCount, Instant lastViewedAt, Instant createdAt, String status) {
    }

    public record PublicPortfolio(String name, List<CredentialItem> credentials, List<BadgeItem> badges) {
    }

    @GetMapping("/users/me/portfolio")
    public ApiResponse<PortfolioResponse> mine(HttpServletRequest request) {
        PortfolioService.Portfolio portfolio = service.get(currentUserProvider.requireUserId());
        return new ApiResponse<>(new PortfolioResponse(
            portfolio.owner().name(), portfolio.owner().joinedAt(), portfolio.summary(),
            portfolio.categoryDistribution(),
            portfolio.completions().stream().map(item -> new CompletionItem(item.courseId(), item.title(),
                item.institutionName(), item.completedAt(), item.classificationCode(), item.credentialId())).toList(),
            portfolio.credentials().stream().map(PortfolioController::credential).toList(),
            portfolio.badges().stream().map(PortfolioController::badge).toList(),
            portfolio.badgeCatalog().stream().map(PortfolioController::catalog).toList()), meta(request));
    }

    @PostMapping("/users/me/portfolio/shares")
    public ResponseEntity<ApiResponse<ShareCreated>> share(
        @RequestBody(required = false) ShareRequest body, HttpServletRequest request
    ) {
        ShareRequest safe = body == null ? new ShareRequest(null, null, null) : body;
        PortfolioService.CreatedShare created = service.createShare(currentUserProvider.requireUserId(),
            safe.credentialIds(), safe.badgeIds(), safe.expiresInDays());
        return ResponseEntity.created(URI.create("/api/v1/users/me/portfolio/shares/" + created.shareId()))
            .body(new ApiResponse<>(new ShareCreated(created.shareId(), created.token(), created.expiresAt()), meta(request)));
    }

    @GetMapping("/users/me/portfolio/shares")
    public ApiResponse<List<ShareItem>> shares(HttpServletRequest request) {
        Instant now = clock.instant();
        return new ApiResponse<>(service.shares(currentUserProvider.requireUserId()).stream()
            .map(share -> new ShareItem(share.id(), share.credentialIds().size(), share.badgeIds().size(),
                share.expiresAt(), share.revokedAt(), share.viewCount(), share.lastViewedAt(), share.createdAt(),
                status(share, now)))
            .toList(), meta(request));
    }

    @DeleteMapping("/users/me/portfolio/shares/{shareId}")
    public ApiResponse<DeletionResponse> revoke(@PathVariable UUID shareId, HttpServletRequest request) {
        service.revokeShare(currentUserProvider.requireUserId(), shareId);
        return new ApiResponse<>(new DeletionResponse(shareId), meta(request));
    }

    @GetMapping("/public/portfolios/{token}")
    public ApiResponse<PublicPortfolio> open(@PathVariable String token, HttpServletRequest request) {
        PortfolioService.PublicPortfolio portfolio = service.open(token);
        return new ApiResponse<>(new PublicPortfolio(portfolio.name(),
            portfolio.credentials().stream().map(PortfolioController::credential).toList(),
            portfolio.badges().stream().map(PortfolioController::badge).toList()), meta(request));
    }

    private static String status(PortfolioRecords.Share share, Instant now) {
        if (share.revokedAt() != null) {
            return "REVOKED";
        }
        return share.expiresAt().isAfter(now) ? "ACTIVE" : "EXPIRED";
    }

    private static CredentialItem credential(PortfolioRecords.CredentialItem item) {
        return new CredentialItem(item.id(), item.credentialNo(), item.courseTitle(), item.institutionName(),
            item.completedAt(), item.issuedAt(), item.status(), "/api/v1/vc/status/" + item.credentialNo());
    }

    private static BadgeItem badge(PortfolioRecords.BadgeItem item) {
        return new BadgeItem(item.id(), item.definitionCode(), item.name(), imageUrl(item.imageFileId()),
            item.issuedAt(), item.tierLevel(), item.tierName());
    }

    private static CatalogItem catalog(PortfolioService.CatalogItem item) {
        return new CatalogItem(item.definition().id(), item.definition().code(), item.definition().name(),
            item.definition().description(), imageUrl(item.definition().imageFileId()), item.criteria(), item.owned(),
            item.badge() == null ? null : badge(item.badge()),
            item.definition().rule() instanceof BadgeRule.Tiered tiered
                ? tiered.levels().stream().map(level -> new TierItem(level.level(), level.name(), level.minCount(),
                    imageUrl(level.imageFileId()))).toList()
                : null);
    }

    private static String imageUrl(UUID fileId) {
        return fileId == null ? null : "/api/v1/files/" + fileId + "/content";
    }

    private ApiMeta meta(HttpServletRequest request) {
        return new ApiMeta((String) request.getAttribute(RequestIdFilter.ATTRIBUTE_NAME),
            OffsetDateTime.ofInstant(clock.instant(), ZoneId.of("Asia/Seoul")));
    }
}
