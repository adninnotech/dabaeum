package com.adn.dabaeum.portfolio.application;

import com.adn.dabaeum.authentication.application.OpaqueTokens;
import com.adn.dabaeum.badge.domain.BadgeCriteria;
import com.adn.dabaeum.badge.domain.BadgeDefinition;
import com.adn.dabaeum.badge.domain.BadgeDefinitionRepository;
import com.adn.dabaeum.classification.application.CourseClassificationService;
import com.adn.dabaeum.common.api.ApiErrorCode;
import com.adn.dabaeum.common.api.ApiException;
import com.adn.dabaeum.portfolio.domain.PortfolioRecords;
import com.adn.dabaeum.portfolio.domain.PortfolioRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 학습포트폴리오와 공유 링크 (설계서 §6). 공개 링크는 이름과 고른 항목만 보인다(연락처 비공개, §6.3). */
@Service
@Profile({"local", "dev"})
public class PortfolioService {

    static final int DEFAULT_DAYS = 30;
    static final int MAX_DAYS = 365;
    static final String UNCLASSIFIED = "UNCLASSIFIED";

    private final PortfolioRepository repository;
    private final ObjectProvider<CourseClassificationService> classifications;
    private final Clock clock;

    private final BadgeDefinitionRepository definitions;

    public PortfolioService(
        PortfolioRepository repository, ObjectProvider<CourseClassificationService> classifications,
        BadgeDefinitionRepository definitions, Clock clock
    ) {
        this.definitions = Objects.requireNonNull(definitions);
        this.repository = Objects.requireNonNull(repository);
        this.classifications = Objects.requireNonNull(classifications);
        this.clock = Objects.requireNonNull(clock);
    }

    public record Summary(int completedCourseCount, BigDecimal totalLearningHours, int badgeCount, int credentialCount) {
    }

    public record CategoryCount(String classificationCode, String classificationName, int completedCount) {
    }

    public record Portfolio(PortfolioRecords.Owner owner, Summary summary, List<CategoryCount> categoryDistribution,
        List<PortfolioRecords.CompletionItem> completions, List<PortfolioRecords.CredentialItem> credentials,
        List<PortfolioRecords.BadgeItem> badges, List<CatalogItem> badgeCatalog) {
    }

    /** 사용 중인 배지 정의 하나와 이 사람의 보유 여부(설계서 §11.4). 보유하지 않았으면 badge 는 null. */
    public record CatalogItem(BadgeDefinition definition, String criteria, boolean owned,
        PortfolioRecords.BadgeItem badge) {
    }

    public record CreatedShare(UUID shareId, String token, Instant expiresAt) {

        @Override
        public String toString() {
            return "CreatedShare[shareId=" + shareId + ", token=***, expiresAt=" + expiresAt + "]";
        }
    }

    public record PublicPortfolio(String name, List<PortfolioRecords.CredentialItem> credentials,
        List<PortfolioRecords.BadgeItem> badges) {
    }

    private List<CatalogItem> catalog(List<PortfolioRecords.BadgeItem> badges) {
        Map<UUID, PortfolioRecords.BadgeItem> owned = new HashMap<>();
        badges.stream().filter(badge -> badge.definitionId() != null)
            .forEach(badge -> owned.putIfAbsent(badge.definitionId(), badge));
        return definitions.findAll(true).stream()
            .map(definition -> new CatalogItem(definition,
                BadgeCriteria.describe(definition.rule(), this::classificationPath),
                owned.containsKey(definition.id()), owned.get(definition.id())))
            .toList();
    }

    private List<String> classificationPath(String code) {
        CourseClassificationService service = classifications.getIfAvailable();
        return service == null ? List.of() : service.pathOf(code);
    }

    @Transactional(readOnly = true)
    public Portfolio get(UUID userId) {
        PortfolioRecords.Owner owner = repository.owner(userId).orElseThrow(() -> new ApiException(
            HttpStatus.NOT_FOUND, ApiErrorCode.USER_NOT_FOUND, "User not found"));
        List<PortfolioRecords.CompletionItem> completions = repository.completions(userId);
        List<PortfolioRecords.CredentialItem> credentials = repository.credentials(userId);
        List<PortfolioRecords.BadgeItem> badges = repository.badges(userId);
        BigDecimal hours = BigDecimal.valueOf(repository.learningMinutes(userId))
            .divide(BigDecimal.valueOf(60), 1, RoundingMode.HALF_UP);
        return new Portfolio(owner,
            new Summary(completions.size(), hours, badges.size(), credentials.size()),
            distribution(completions), completions, credentials, badges, catalog(badges));
    }

    @Transactional
    public CreatedShare createShare(UUID userId, List<UUID> credentialIds, List<UUID> badgeIds, Integer expiresInDays) {
        int days = expiresInDays == null ? DEFAULT_DAYS : expiresInDays;
        if (days < 1 || days > MAX_DAYS) {
            throw validation("expiresInDays");
        }
        List<UUID> credentials = pick(credentialIds,
            repository.credentials(userId).stream().map(PortfolioRecords.CredentialItem::id).toList(), "credentialIds");
        List<UUID> badges = pick(badgeIds,
            repository.badges(userId).stream().map(PortfolioRecords.BadgeItem::id).toList(), "badgeIds");
        Instant now = clock.instant();
        String token = OpaqueTokens.newToken();
        PortfolioRecords.Share share = new PortfolioRecords.Share(UUID.randomUUID(), userId, credentials, badges,
            now.plus(Duration.ofDays(days)), null, 0, null, now);
        repository.saveShare(share, OpaqueTokens.sha256Hex(token));
        return new CreatedShare(share.id(), token, share.expiresAt());
    }

    @Transactional(readOnly = true)
    public List<PortfolioRecords.Share> shares(UUID userId) {
        return repository.shares(userId);
    }

    @Transactional
    public void revokeShare(UUID userId, UUID shareId) {
        if (!repository.revokeShare(shareId, userId, clock.instant())) {
            throw notFound();
        }
    }

    /** 만료·폐기·없음은 모두 같은 404. 열 때마다 조회 수가 오른다. 그 사이 폐기된 수료증·배지는 빠진다. */
    @Transactional
    public PublicPortfolio open(String token) {
        if (token == null || token.isBlank()) {
            throw notFound();
        }
        PortfolioRecords.Share share = repository.openShare(OpaqueTokens.sha256Hex(token.trim()), clock.instant())
            .orElseThrow(PortfolioService::notFound);
        String name = repository.owner(share.userId()).map(PortfolioRecords.Owner::name).orElseThrow(PortfolioService::notFound);
        Set<UUID> credentialIds = Set.copyOf(share.credentialIds());
        Set<UUID> badgeIds = Set.copyOf(share.badgeIds());
        return new PublicPortfolio(name,
            repository.credentials(share.userId()).stream().filter(item -> credentialIds.contains(item.id())).toList(),
            repository.badges(share.userId()).stream().filter(item -> badgeIds.contains(item.id())).toList());
    }

    private List<CategoryCount> distribution(List<PortfolioRecords.CompletionItem> completions) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (PortfolioRecords.CompletionItem item : completions) {
            String code = item.classificationCode();
            String top = code == null || code.length() < 7 ? UNCLASSIFIED : code.substring(0, 7) + "0000";
            counts.merge(top, 1, Integer::sum);
        }
        CourseClassificationService service = classifications.getIfAvailable();
        return counts.entrySet().stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
            .map(entry -> new CategoryCount(entry.getKey(), name(service, entry.getKey()), entry.getValue()))
            .toList();
    }

    private static String name(CourseClassificationService service, String code) {
        if (UNCLASSIFIED.equals(code) || service == null) {
            return UNCLASSIFIED.equals(code) ? "미분류" : code;
        }
        List<String> path = service.pathOf(code);
        return path.isEmpty() ? code : path.getFirst();
    }

    /** 생략하면 전체, 주면 본인 소유만(아니면 422). */
    private static List<UUID> pick(List<UUID> requested, List<UUID> owned, String field) {
        if (requested == null) {
            return owned;
        }
        Set<UUID> ownedSet = Set.copyOf(owned);
        if (!ownedSet.containsAll(requested)) {
            throw validation(field);
        }
        return requested.stream().distinct().collect(Collectors.toList());
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, ApiErrorCode.PORTFOLIO_SHARE_NOT_FOUND, "Portfolio share not found");
    }

    private static ApiException validation(String field) {
        return new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, ApiErrorCode.VALIDATION_FAILED,
            "Request validation failed", List.of(field));
    }
}
