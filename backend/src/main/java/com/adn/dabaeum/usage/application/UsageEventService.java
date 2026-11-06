package com.adn.dabaeum.usage.application;

import com.adn.dabaeum.banner.domain.BannerRepository;
import com.adn.dabaeum.usage.domain.UsageEvent;
import com.adn.dabaeum.usage.domain.UsageEventRepository;
import com.adn.dabaeum.usage.domain.UsageEventType;
import com.adn.dabaeum.usage.domain.UsageSaltRepository;
import com.adn.dabaeum.usage.domain.VisitorSegment;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 방문·검색·배너 클릭 기록 (docs/specs/usage-statistics.md §3, §4). 같은 사람은 하루 1건.
 * 키는 HMAC(당일 salt, 회원 id 또는 ip‖UA) — salt 를 다음 날 지우므로 그 뒤엔 키를 다시 만들 수 없다. user_id·IP·UA 원문은 남기지 않는다.
 */
@Service
@Profile({"local", "dev"})
public class UsageEventService {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final UsageEventRepository events;
    private final UsageSaltRepository salts;
    private final BannerRepository banners;
    private final Clock clock;

    public UsageEventService(UsageEventRepository events, UsageSaltRepository salts, BannerRepository banners, Clock clock) {
        this.events = Objects.requireNonNull(events);
        this.salts = Objects.requireNonNull(salts);
        this.banners = Objects.requireNonNull(banners);
        this.clock = Objects.requireNonNull(clock);
    }

    @Transactional
    public void recordVisit(UUID userId, String ipAddress, String userAgent) {
        record(UsageEventType.VISIT, "", false, userId, ipAddress, userAgent);
    }

    @Transactional
    public void recordSearch(String query, boolean zeroResult, UUID userId, String ipAddress, String userAgent) {
        record(UsageEventType.SEARCH, SearchKeyword.normalize(query), zeroResult, userId, ipAddress, userAgent);
    }

    /** 지금 노출 중인 배너가 아니면 조용히 무시한다(존재 여부를 응답으로 알리지 않는다). */
    @Transactional
    public void recordBannerClick(UUID bannerId, UUID userId, String ipAddress, String userAgent) {
        if (bannerId == null) {
            return;
        }
        Instant now = clock.instant();
        boolean visible = banners.findById(bannerId)
            .filter(banner -> banner.deletedAt() == null && banner.isVisibleAt(now))
            .isPresent();
        if (visible) {
            record(UsageEventType.BANNER_CLICK, bannerId.toString(), false, userId, ipAddress, userAgent);
        }
    }

    private void record(
        UsageEventType type, String targetKey, boolean zeroResult, UUID userId, String ipAddress, String userAgent
    ) {
        Instant now = clock.instant();
        LocalDate today = now.atZone(SEOUL).toLocalDate();
        VisitorSegment segment = userId == null
            ? VisitorSegment.GUEST
            : events.findMember(userId)
                .map(member -> VisitorSegment.member(member.gender(), member.birthDate(), today))
                .orElseGet(() -> VisitorSegment.member(null, null, today));
        String viewerKey = viewerKey(salts.saltFor(today), userId, ipAddress, userAgent);
        events.record(new UsageEvent(type, today, viewerKey, targetKey, segment, zeroResult, now));
    }

    /** 테스트가 기대 키를 만들 때도 쓴다. */
    public static String viewerKey(byte[] salt, UUID userId, String ipAddress, String userAgent) {
        String subject = userId != null
            ? "u:" + userId
            : "g:" + ipAddress + "\n" + userAgent;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(salt, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(subject.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("HmacSHA256 is not available", exception);
        }
    }
}
