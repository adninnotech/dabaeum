package com.adn.dabaeum.badge.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.adn.dabaeum.badge.domain.LearningBadgeEvent;
import com.adn.dabaeum.badge.domain.LearningBadgeEventRepository;
import com.adn.dabaeum.badge.domain.LearningBadgeRepository;
import com.adn.dabaeum.badge.domain.LearningBadgeRepository.BadgeSearch;
import com.adn.dabaeum.badge.domain.LearningBadgeStatus;
import com.adn.dabaeum.common.api.ApiErrorCode;
import com.adn.dabaeum.common.api.ApiException;
import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** 설계서 §11.4 — 관리자 배지 현황 검색과 변동 이력 조회. */
@ExtendWith(MockitoExtension.class)
class BadgeStatusServiceTest {

    @Mock LearningBadgeRepository badges;
    @Mock LearningBadgeEventRepository events;
    @Mock LearningBadgeApplicationService badgeService;
    @Mock com.adn.dabaeum.badge.domain.BadgeAwardRepository awards;
    private BadgeStatusService service;
    private static final Instant NOW = Instant.parse("2026-09-29T09:00:00Z");

    @BeforeEach
    void setUp() {
        service = new BadgeStatusService(badges, events, badgeService, awards,
            java.time.Clock.fixed(NOW, java.time.ZoneOffset.UTC));
    }

    @Test
    void adminRevokeRecordsReasonAndActor() {
        UUID badge = UUID.randomUUID();
        UUID admin = UUID.randomUUID();
        when(badges.findById(badge)).thenReturn(java.util.Optional.of(badge(badge, LearningBadgeStatus.ISSUED, 2)))
            .thenReturn(java.util.Optional.of(badge(badge, LearningBadgeStatus.REVOKED, 2)));
        when(awards.revoke(badge, NOW)).thenReturn(true);

        var revoked = service.revoke(badge, "  테스트 데이터 정리 ", admin);

        assertThat(revoked.status()).isEqualTo(LearningBadgeStatus.REVOKED);
        ArgumentCaptor<LearningBadgeEvent> event = ArgumentCaptor.forClass(LearningBadgeEvent.class);
        verify(events).save(event.capture());
        assertThat(event.getValue().type()).isEqualTo(LearningBadgeEvent.Type.REVOKED);
        assertThat(event.getValue().reason()).isEqualTo("테스트 데이터 정리");
        assertThat(event.getValue().actorId()).isEqualTo(admin);
        assertThat(event.getValue().fromLevel()).isEqualTo(2);
    }

    @Test
    void revokeRejectsMissingReasonUnknownOrAlreadyRevokedBadge() {
        UUID badge = UUID.randomUUID();
        assertCode(() -> service.revoke(badge, " ", UUID.randomUUID()), ApiErrorCode.VALIDATION_FAILED);
        when(badges.findById(badge)).thenReturn(java.util.Optional.empty());
        assertCode(() -> service.revoke(badge, "사유", UUID.randomUUID()), ApiErrorCode.BADGE_NOT_FOUND);
        when(badges.findById(badge)).thenReturn(java.util.Optional.of(badge(badge, LearningBadgeStatus.REVOKED, null)));
        assertCode(() -> service.revoke(badge, "사유", UUID.randomUUID()), ApiErrorCode.BADGE_CONFLICT);
    }

    private static com.adn.dabaeum.badge.domain.LearningBadge badge(UUID id, LearningBadgeStatus status, Integer tier) {
        return new com.adn.dabaeum.badge.domain.LearningBadge(id, UUID.randomUUID(), null, null, "TIERED", "단계",
            status, null, NOW, null, NOW, NOW, UUID.randomUUID(), "TIER", null,
            new com.adn.dabaeum.badge.domain.LearningBadge.Details("학습자", tier, null, null, null));
    }

    @Test
    void searchNormalizesKeywordAndPages() {
        UUID definition = UUID.randomUUID();
        when(badges.search(any())).thenReturn(List.of());
        when(badges.count(any())).thenReturn(41L);

        BadgeStatusService.Page page = service.search(definition, LearningBadgeStatus.ISSUED, 2, " 김_학 ", 1, 20);

        ArgumentCaptor<BadgeSearch> criteria = ArgumentCaptor.forClass(BadgeSearch.class);
        verify(badges).search(criteria.capture());
        assertThat(criteria.getValue().keyword()).isEqualTo("김\\_학");
        assertThat(criteria.getValue().offset()).isEqualTo(20);
        assertThat(criteria.getValue().tierLevel()).isEqualTo(2);
        assertThat(page.totalElements()).isEqualTo(41);
        assertThat(page.totalPages()).isEqualTo(3);
    }

    @Test
    void invalidPagingOrTierIsRejected() {
        assertCode(() -> service.search(null, null, null, null, -1, 20), ApiErrorCode.BAD_REQUEST);
        assertCode(() -> service.search(null, null, null, null, 0, 101), ApiErrorCode.BAD_REQUEST);
        assertCode(() -> service.search(null, null, 11, null, 0, 20), ApiErrorCode.BAD_REQUEST);
    }

    @Test
    void historyChecksAccessThroughBadgeLookup() {
        UUID badge = UUID.randomUUID();
        AuthenticatedUserContext actor = new AuthenticatedUserContext(UUID.randomUUID(), "LOCAL", Set.of());
        LearningBadgeEvent event = LearningBadgeEvent.of(badge, LearningBadgeEvent.Type.ISSUED, null, 1, "발급", null,
            Instant.parse("2026-09-29T00:00:00Z"));
        when(events.findByBadgeId(badge)).thenReturn(List.of(event));

        assertThat(service.history(badge, actor)).containsExactly(event);
        verify(badgeService).get(badge, actor);
    }

    private static void assertCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, ApiErrorCode code) {
        assertThatThrownBy(call).isInstanceOfSatisfying(ApiException.class,
            exception -> assertThat(exception.code()).isEqualTo(code));
    }
}
