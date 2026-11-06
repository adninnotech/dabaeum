package com.adn.dabaeum.banner.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BannerVisibilityTest {

    private static final Instant NOW = Instant.parse("2026-09-16T01:00:00Z");

    @Test
    void 공개는_항상_보인다() {
        assertThat(banner(BannerVisibility.PUBLIC, null, null).isVisibleAt(NOW)).isTrue();
    }

    @Test
    void 비공개는_항상_안_보인다() {
        assertThat(banner(BannerVisibility.PRIVATE, null, null).isVisibleAt(NOW)).isFalse();
    }

    @Test
    void 기간공개는_기간_안에서만_보인다() {
        var scheduled = banner(BannerVisibility.SCHEDULED,
            Instant.parse("2026-09-01T00:00:00Z"),
            Instant.parse("2026-09-30T00:00:00Z"));

        assertThat(scheduled.isVisibleAt(NOW)).isTrue();
        assertThat(scheduled.isVisibleAt(Instant.parse("2026-08-31T23:59:59Z"))).isFalse();
        assertThat(scheduled.isVisibleAt(Instant.parse("2026-10-01T00:00:00Z"))).isFalse();
    }

    @Test
    void 기간_경계는_포함이다() {
        var scheduled = banner(BannerVisibility.SCHEDULED,
            Instant.parse("2026-09-16T01:00:00Z"),
            Instant.parse("2026-09-16T01:00:00Z"));

        assertThat(scheduled.isVisibleAt(NOW)).isTrue();
    }

    @Test
    void 기간공개인데_기간이_없으면_생성이_거부된다() {
        assertThatThrownBy(() -> banner(BannerVisibility.SCHEDULED, null, null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 이미_저장된_PUBLIC_기간_행도_조회는_깨지지_않는다() {
        // PUBLIC·PRIVATE 의 기간 금지는 서비스 계층에서만 막는다(422). 도메인 생성자는 막지
        // 않으므로, 과거에 잘못 들어간 행을 읽는 경로(관리자 목록 등)는 계속 동작해야 한다.
        var legacy = banner(BannerVisibility.PUBLIC,
            Instant.parse("2026-09-01T00:00:00Z"), Instant.parse("2026-09-30T00:00:00Z"));

        assertThat(legacy.isVisibleAt(Instant.parse("2099-01-01T00:00:00Z"))).isTrue();
    }

    private Banner banner(BannerVisibility visibility, Instant startAt, Instant endAt) {
        return new Banner(
            UUID.randomUUID(), null, "배너", null, null, null,
            visibility, startAt, endAt, 0, null, null, NOW, NOW, null);
    }
}
