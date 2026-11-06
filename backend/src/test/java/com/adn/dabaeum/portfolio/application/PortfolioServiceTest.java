package com.adn.dabaeum.portfolio.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.adn.dabaeum.common.api.ApiErrorCode;
import com.adn.dabaeum.common.api.ApiException;
import com.adn.dabaeum.portfolio.domain.PortfolioRecords;
import com.adn.dabaeum.portfolio.domain.PortfolioRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

/** 설계서 §6 — 공유 기간·소유 검증, 분야 분포. */
@ExtendWith(MockitoExtension.class)
class PortfolioServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-28T00:00:00Z");
    private static final UUID USER = UUID.randomUUID();
    private static final UUID MINE = UUID.randomUUID();

    @Mock PortfolioRepository repository;
    @Mock com.adn.dabaeum.badge.domain.BadgeDefinitionRepository definitions;
    private PortfolioService service;

    @BeforeEach
    void setUp() {
        service = new PortfolioService(repository,
            new StaticListableBeanFactory().getBeanProvider(
                com.adn.dabaeum.classification.application.CourseClassificationService.class),
            definitions, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void omittedSelectionSharesEverythingForThirtyDaysAndStoresOnlyTheHash() {
        when(repository.credentials(USER)).thenReturn(List.of(credential(MINE)));
        when(repository.badges(USER)).thenReturn(List.of());

        PortfolioService.CreatedShare created = service.createShare(USER, null, null, null);

        ArgumentCaptor<PortfolioRecords.Share> share = ArgumentCaptor.forClass(PortfolioRecords.Share.class);
        ArgumentCaptor<String> hash = ArgumentCaptor.forClass(String.class);
        verify(repository).saveShare(share.capture(), hash.capture());
        assertThat(share.getValue().credentialIds()).containsExactly(MINE);
        assertThat(share.getValue().expiresAt()).isEqualTo(NOW.plusSeconds(30L * 86_400));
        assertThat(hash.getValue()).hasSize(64).isNotEqualTo(created.token());
        assertThat(created.toString()).doesNotContain(created.token());
    }

    @Test
    void someoneElsesCredentialIsRejected() {
        when(repository.credentials(USER)).thenReturn(List.of(credential(MINE)));

        assertThatThrownBy(() -> service.createShare(USER, List.of(UUID.randomUUID()), null, 10))
            .isInstanceOfSatisfying(ApiException.class,
                exception -> assertThat(exception.code()).isEqualTo(ApiErrorCode.VALIDATION_FAILED));
        verify(repository, never()).saveShare(any(), anyString());
    }

    @Test
    void expiryOutsideOneToThreeSixtyFiveDaysIsRejected() {
        assertThatThrownBy(() -> service.createShare(USER, null, null, 366)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.createShare(USER, null, null, 0)).isInstanceOf(ApiException.class);
    }

    @Test
    void closedOrUnknownShareIsNotFound() {
        when(repository.openShare(anyString(), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.open("anything"))
            .isInstanceOfSatisfying(ApiException.class,
                exception -> assertThat(exception.code()).isEqualTo(ApiErrorCode.PORTFOLIO_SHARE_NOT_FOUND));
    }

    @Test
    void distributionGroupsByFirstLevelAndCountsUnclassified() {
        when(repository.owner(USER)).thenReturn(Optional.of(new PortfolioRecords.Owner("학습자", NOW)));
        when(repository.completions(USER)).thenReturn(List.of(
            completion("TSIX_040201"), completion("TSIX_040301"), completion(null)));
        when(repository.credentials(USER)).thenReturn(List.of());
        when(repository.badges(USER)).thenReturn(List.of());
        when(repository.learningMinutes(USER)).thenReturn(95L);

        PortfolioService.Portfolio portfolio = service.get(USER);

        assertThat(portfolio.categoryDistribution()).extracting(PortfolioService.CategoryCount::classificationCode)
            .containsExactly("TSIX_040000", "UNCLASSIFIED");
        assertThat(portfolio.categoryDistribution().get(0).completedCount()).isEqualTo(2);
        assertThat(portfolio.summary().totalLearningHours()).isEqualByComparingTo("1.6");
    }

    @Test
    void badgeCatalogListsOwnedAndNotOwnedActiveDefinitions() {
        com.adn.dabaeum.badge.domain.BadgeDefinition owned = definition("TIER_A",
            new com.adn.dabaeum.badge.domain.BadgeRule.Tiered(List.of(
                new com.adn.dabaeum.badge.domain.BadgeRule.TierLevel(1, "씨앗", 1),
                new com.adn.dabaeum.badge.domain.BadgeRule.TierLevel(2, "새싹", 6)), null));
        com.adn.dabaeum.badge.domain.BadgeDefinition missing = definition("CULTURE_3",
            new com.adn.dabaeum.badge.domain.BadgeRule.Cumulative(3, null));
        when(definitions.findAll(true)).thenReturn(List.of(owned, missing));
        UUID badge = UUID.randomUUID();
        when(repository.owner(USER)).thenReturn(Optional.of(new PortfolioRecords.Owner("학습자", NOW)));
        when(repository.completions(USER)).thenReturn(List.of());
        when(repository.credentials(USER)).thenReturn(List.of());
        when(repository.badges(USER)).thenReturn(List.of(new PortfolioRecords.BadgeItem(badge, owned.id(), "TIER_A",
            "단계", null, NOW, 2, "새싹")));

        PortfolioService.Portfolio portfolio = service.get(USER);

        assertThat(portfolio.badgeCatalog()).hasSize(2);
        assertThat(portfolio.badgeCatalog().get(0)).satisfies(item -> {
            assertThat(item.owned()).isTrue();
            assertThat(item.badge().tierName()).isEqualTo("새싹");
            assertThat(item.criteria()).contains("씨앗 1~5건");
        });
        assertThat(portfolio.badgeCatalog().get(1)).satisfies(item -> {
            assertThat(item.owned()).isFalse();
            assertThat(item.badge()).isNull();
            assertThat(item.criteria()).isEqualTo("강좌 누적 3건 이수");
        });
    }

    private static com.adn.dabaeum.badge.domain.BadgeDefinition definition(String code,
        com.adn.dabaeum.badge.domain.BadgeRule rule) {
        return new com.adn.dabaeum.badge.domain.BadgeDefinition(UUID.randomUUID(), code, "배지 " + code, null, null,
            rule, true, NOW, NOW);
    }

    /** 포트폴리오는 여러 조회를 모으므로 한 읽기 트랜잭션 안에서 읽어야 목록과 카탈로그가 어긋나지 않는다. */
    @Test
    void portfolioIsReadInOneReadOnlyTransaction() throws NoSuchMethodException {
        var transactional = PortfolioService.class.getMethod("get", UUID.class)
            .getAnnotation(org.springframework.transaction.annotation.Transactional.class);
        assertThat(transactional).isNotNull();
        assertThat(transactional.readOnly()).isTrue();
    }

    private static PortfolioRecords.CredentialItem credential(UUID id) {
        return new PortfolioRecords.CredentialItem(id, "CRED-1", "강좌", "기관", NOW, NOW, "ISSUED");
    }

    private static PortfolioRecords.CompletionItem completion(String classification) {
        return new PortfolioRecords.CompletionItem(UUID.randomUUID(), UUID.randomUUID(), "강좌", "기관", NOW,
            classification, null);
    }
}
