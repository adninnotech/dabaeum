package com.adn.dabaeum.banner;

import static org.assertj.core.api.Assertions.assertThat;

import com.adn.dabaeum.banner.domain.Banner;
import com.adn.dabaeum.banner.domain.BannerRepository;
import com.adn.dabaeum.banner.domain.BannerVisibility;
import com.adn.dabaeum.support.RemotePostgresIntegrationTestSupport;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class BannerMapperIntegrationTest extends RemotePostgresIntegrationTestSupport {

    @Autowired
    BannerRepository repository;

    @Test
    void savesEveryVisibilityAndReadsThemBackInSortOrder() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        // sort_order 를 뒤섞어 넣어 SQL 의 ORDER BY 가 실제로 도는지 본다.
        Banner priv = banner("비공개", BannerVisibility.PRIVATE, 9003, null, null, now);
        Banner scheduled = banner("기간공개", BannerVisibility.SCHEDULED, 9001,
            now.minus(1, ChronoUnit.DAYS), now.plus(1, ChronoUnit.DAYS), now);
        Banner open = banner("공개", BannerVisibility.PUBLIC, 9002, null, null, now);
        repository.save(priv);
        repository.save(scheduled);
        repository.save(open);

        List<UUID> ours = ids(repository.findAll(), scheduled, open, priv);

        assertThat(ours).containsExactly(scheduled.id(), open.id(), priv.id());
        assertThat(repository.findById(scheduled.id())).contains(scheduled);
        // 노출 판정은 DB 가 아니라 도메인이 한다. 기간 안이면 지금 보인다.
        assertThat(repository.findById(scheduled.id()).orElseThrow().isVisibleAt(now)).isTrue();
        assertThat(repository.findById(priv.id()).orElseThrow().isVisibleAt(now)).isFalse();
    }

    @Test
    void softDeletedBannersDisappearFromTheList() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        Banner banner = banner("지울 배너", BannerVisibility.PUBLIC, 9100, null, null, now);
        repository.save(banner);

        assertThat(repository.softDelete(banner.id(), now)).isTrue();

        assertThat(repository.findAll()).noneMatch(row -> row.id().equals(banner.id()));
        // 두 번째 삭제는 이미 지워진 행이라 아무것도 건드리지 못한다.
        assertThat(repository.softDelete(banner.id(), now)).isFalse();
    }

    @Test
    void databaseRejectsScheduledBannerWithoutAPeriod() {
        // 도메인 생성자를 우회해 직접 넣어도 DB CHECK 가 막는다.
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        assertSqlState("23514", () -> jdbcTemplate.update("""
            INSERT INTO tb_banners (
                id, title, visibility, sort_order, created_at, updated_at
            ) VALUES (?, ?, 'SCHEDULED', 9200, ?, ?)
            """, UUID.randomUUID(), "기간 없는 기간공개", now, now));
    }

    private List<UUID> ids(List<Banner> all, Banner... mine) {
        List<UUID> wanted = List.of(mine).stream().map(Banner::id).toList();
        return all.stream().map(Banner::id).filter(wanted::contains).toList();
    }

    private Banner banner(
        String title,
        BannerVisibility visibility,
        int sortOrder,
        Instant startAt,
        Instant endAt,
        Instant now
    ) {
        return new Banner(
            UUID.randomUUID(), "IT" + UUID.randomUUID().toString().substring(0, 8),
            title + "-" + UUID.randomUUID().toString().substring(0, 8),
            null, null, null,
            visibility, startAt, endAt, sortOrder, null, null, now, now, null);
    }
}
