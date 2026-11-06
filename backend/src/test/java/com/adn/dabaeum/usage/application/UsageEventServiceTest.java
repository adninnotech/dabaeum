package com.adn.dabaeum.usage.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.adn.dabaeum.banner.domain.Banner;
import com.adn.dabaeum.banner.domain.BannerRepository;
import com.adn.dabaeum.banner.domain.BannerVisibility;
import com.adn.dabaeum.usage.domain.MemberProfile;
import com.adn.dabaeum.usage.domain.UsageEvent;
import com.adn.dabaeum.usage.domain.UsageEventRepository;
import com.adn.dabaeum.usage.domain.UsageEventType;
import com.adn.dabaeum.usage.domain.UsageSaltRepository;
import com.adn.dabaeum.usage.domain.VisitorSegment;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** docs/specs/usage-statistics.md §3, §4 — 익명 키는 당일 salt HMAC, 회원은 구간값만, user_id·IP 원문은 남기지 않는다. */
@ExtendWith(MockitoExtension.class)
class UsageEventServiceTest {

    // UTC 2026-10-29 15:30 = 서울 10/30 00:30
    private static final Instant NOW = Instant.parse("2026-10-29T15:30:00Z");
    private static final LocalDate SEOUL_DAY = LocalDate.of(2026, 10, 30);
    private static final byte[] SALT = "salt-of-the-day-32-bytes-long!!!".getBytes(StandardCharsets.UTF_8);
    private static final UUID USER = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Mock UsageEventRepository events;
    @Mock UsageSaltRepository salts;
    @Mock BannerRepository banners;
    private UsageEventService service;

    @BeforeEach
    void setUp() {
        service = new UsageEventService(events, salts, banners, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void guestVisitKeepsOnlyHashedKeyAndGuestSegment() {
        when(salts.saltFor(SEOUL_DAY)).thenReturn(SALT);

        service.recordVisit(null, "203.0.113.7", "junit");

        UsageEvent event = captured();
        assertThat(event.type()).isEqualTo(UsageEventType.VISIT);
        assertThat(event.date()).isEqualTo(SEOUL_DAY);
        assertThat(event.targetKey()).isEmpty();
        assertThat(event.segment()).isEqualTo(VisitorSegment.GUEST);
        assertThat(event.viewerKey()).hasSize(64).doesNotContain("203.0.113.7");
    }

    @Test
    void memberVisitSnapshotsSegmentAndKeyDoesNotRevealUserId() {
        when(salts.saltFor(SEOUL_DAY)).thenReturn(SALT);
        when(events.findMember(USER)).thenReturn(Optional.of(new MemberProfile("FEMALE", LocalDate.of(1990, 1, 1))));

        service.recordVisit(USER, "203.0.113.7", "junit");

        UsageEvent event = captured();
        assertThat(event.segment()).isEqualTo(new VisitorSegment("MEMBER", "FEMALE", "30"));
        assertThat(event.viewerKey()).hasSize(64)
            .isNotEqualTo(UsageEventService.viewerKey(SALT, null, "203.0.113.7", "junit"))
            .isEqualTo(UsageEventService.viewerKey(SALT, USER, "198.51.100.1", "other-device"));
    }

    @Test
    void memberMissingFromUsersCountsAsMemberWithUnknownSegment() {
        when(salts.saltFor(SEOUL_DAY)).thenReturn(SALT);
        when(events.findMember(USER)).thenReturn(Optional.empty());

        service.recordVisit(USER, "203.0.113.7", "junit");

        assertThat(captured().segment()).isEqualTo(new VisitorSegment("MEMBER", "UNKNOWN", "UNKNOWN"));
    }

    @Test
    void searchStoresNormalizedKeywordAndZeroResultFlag() {
        when(salts.saltFor(SEOUL_DAY)).thenReturn(SALT);

        service.recordSearch("  Python   기초 ", true, null, "203.0.113.7", "junit");

        UsageEvent event = captured();
        assertThat(event.type()).isEqualTo(UsageEventType.SEARCH);
        assertThat(event.targetKey()).isEqualTo("python 기초");
        assertThat(event.zeroResult()).isTrue();
    }

    @Test
    void visibleBannerClickIsRecordedWithBannerId() {
        UUID banner = UUID.randomUUID();
        when(banners.findById(banner)).thenReturn(Optional.of(banner(banner, BannerVisibility.PUBLIC, null)));
        when(salts.saltFor(SEOUL_DAY)).thenReturn(SALT);

        service.recordBannerClick(banner, null, "203.0.113.7", "junit");

        UsageEvent event = captured();
        assertThat(event.type()).isEqualTo(UsageEventType.BANNER_CLICK);
        assertThat(event.targetKey()).isEqualTo(banner.toString());
    }

    @Test
    void hiddenDeletedOrMissingBannerClickIsIgnored() {
        UUID hidden = UUID.randomUUID();
        UUID deleted = UUID.randomUUID();
        UUID missing = UUID.randomUUID();
        when(banners.findById(hidden)).thenReturn(Optional.of(banner(hidden, BannerVisibility.PRIVATE, null)));
        when(banners.findById(deleted)).thenReturn(Optional.of(banner(deleted, BannerVisibility.PUBLIC, NOW)));
        when(banners.findById(missing)).thenReturn(Optional.empty());

        service.recordBannerClick(hidden, null, "203.0.113.7", "junit");
        service.recordBannerClick(deleted, null, "203.0.113.7", "junit");
        service.recordBannerClick(missing, null, "203.0.113.7", "junit");
        service.recordBannerClick(null, null, "203.0.113.7", "junit");

        verify(events, never()).record(any());
    }

    private UsageEvent captured() {
        ArgumentCaptor<UsageEvent> event = ArgumentCaptor.forClass(UsageEvent.class);
        verify(events).record(event.capture());
        return event.getValue();
    }

    private static Banner banner(UUID id, BannerVisibility visibility, Instant deletedAt) {
        return new Banner(id, null, "배너", null, null, null, visibility, null, null, 0, null, null,
            NOW, NOW, deletedAt);
    }
}
