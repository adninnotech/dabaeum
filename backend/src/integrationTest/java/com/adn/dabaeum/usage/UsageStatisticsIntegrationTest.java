package com.adn.dabaeum.usage;

import static org.assertj.core.api.Assertions.assertThat;

import com.adn.dabaeum.support.RemotePostgresIntegrationTestSupport;
import com.adn.dabaeum.usage.application.UsageEventService;
import com.adn.dabaeum.usage.domain.BannerClickCount;
import com.adn.dabaeum.usage.domain.KeywordCount;
import com.adn.dabaeum.usage.domain.KeywordRankingType;
import com.adn.dabaeum.usage.domain.SegmentCount;
import com.adn.dabaeum.usage.domain.UsageCounts;
import com.adn.dabaeum.usage.domain.UsageEvent;
import com.adn.dabaeum.usage.domain.UsageEventRepository;
import com.adn.dabaeum.usage.domain.UsageEventType;
import com.adn.dabaeum.usage.domain.UsageInterval;
import com.adn.dabaeum.usage.domain.UsageMetric;
import com.adn.dabaeum.usage.domain.UsageSaltRepository;
import com.adn.dabaeum.usage.domain.UsageStatisticsRepository;
import com.adn.dabaeum.usage.domain.UsageTrendRow;
import com.adn.dabaeum.usage.domain.VisitorSegment;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * docs/specs/usage-statistics.md §4·§5·§6 — V59 기록·일별 집계·조회 SQL.
 * dev DB 의 실제 기록과 섞이지 않게 집계 날짜는 먼 미래(2031-01)를 쓴다. 모두 롤백된다.
 */
class UsageStatisticsIntegrationTest extends RemotePostgresIntegrationTestSupport {

    private static final LocalDate DAY = LocalDate.of(2031, 1, 5);
    private static final Instant AT = DAY.atTime(10, 0).atZone(ZoneId.of("Asia/Seoul")).toInstant();

    @Autowired UsageEventService service;
    @Autowired UsageEventRepository events;
    @Autowired UsageStatisticsRepository statistics;
    @Autowired UsageSaltRepository salts;

    @Test
    void sameVisitorCountsOncePerDayAndNoUserIdIsStored() {
        UUID user = insertUser("FEMALE", LocalDate.of(1990, 1, 1));

        service.recordVisit(null, "203.0.113.7", "junit-usage");
        service.recordVisit(null, "203.0.113.7", "junit-usage");
        service.recordVisit(user, "203.0.113.7", "junit-usage");
        service.recordVisit(user, "198.51.100.9", "other-device");

        byte[] salt = salts.saltFor(LocalDate.now(ZoneId.of("Asia/Seoul")));
        List<String> keys = List.of(
            UsageEventService.viewerKey(salt, null, "203.0.113.7", "junit-usage"),
            UsageEventService.viewerKey(salt, user, "203.0.113.7", "junit-usage"));
        assertThat(jdbcTemplate.queryForList(
            "SELECT member_type || ':' || gender || ':' || age_band FROM tb_usage_events WHERE event_type = 'VISIT' AND viewer_key IN (?, ?)",
            String.class, keys.get(0), keys.get(1)))
            .containsExactlyInAnyOrder("GUEST:UNKNOWN:UNKNOWN", "MEMBER:FEMALE:30");
        assertThat(count("""
            SELECT COUNT(*) FROM information_schema.columns
             WHERE table_name = 'tb_usage_events' AND column_name IN ('user_id', 'ip_address', 'user_agent')
            """)).isZero();
    }

    @Test
    void dayAggregationCountsEveryMetricBySegmentAndKeepsOnlyFrequentKeywords() {
        UUID woman = insertUser("FEMALE", LocalDate.of(1990, 1, 1));
        UUID course = insertCourse();
        UUID banner = insertBanner();
        record(UsageEventType.VISIT, "g1", "", VisitorSegment.GUEST, false);
        record(UsageEventType.VISIT, "m1", "", new VisitorSegment("MEMBER", "FEMALE", "40"), false);
        for (String viewer : List.of("s1", "s2", "s3")) {
            record(UsageEventType.SEARCH, viewer, "엑셀", VisitorSegment.GUEST, viewer.equals("s1"));
        }
        record(UsageEventType.SEARCH, "s1", "드론", VisitorSegment.GUEST, false);
        record(UsageEventType.SEARCH, "s2", "드론", VisitorSegment.GUEST, false);
        for (String viewer : List.of("s1", "s2", "s3")) {
            record(UsageEventType.SEARCH, viewer, UsageEvent.MASKED_KEYWORD, VisitorSegment.GUEST, false);
        }
        record(UsageEventType.BANNER_CLICK, "b1", banner.toString(), VisitorSegment.GUEST, false);
        jdbcTemplate.update("""
            INSERT INTO tb_course_views (course_id, viewer_key, view_date, user_id, viewed_at)
            VALUES (?, ?, ?, ?, ?), (?, ?, ?, NULL, ?)
            """, course, "v".repeat(64), DAY, woman, java.sql.Timestamp.from(AT),
            course, "w".repeat(64), DAY, java.sql.Timestamp.from(AT));
        jdbcTemplate.update("INSERT INTO tb_enrollments (id, course_id, user_id, status, applied_at) VALUES (?, ?, ?, 'APPLIED', ?)",
            UUID.randomUUID(), course, woman, java.sql.Timestamp.from(AT));

        statistics.aggregateDay(DAY, 3, AT);
        statistics.aggregateDay(DAY, 3, AT);

        assertThat(statistics.lastAggregatedDay()).contains(DAY);
        assertThat(statistics.totals(DAY, DAY)).isEqualTo(new UsageCounts(2, 8, 2, 1, 1));
        assertThat(statistics.segments(UsageMetric.COURSE_VIEW, DAY, DAY)).containsExactlyInAnyOrder(
            new SegmentCount("MEMBER", "FEMALE", "40", 1), new SegmentCount("GUEST", "UNKNOWN", "UNKNOWN", 1));
        assertThat(statistics.segments(UsageMetric.ENROLLMENT, DAY, DAY))
            .containsExactly(new SegmentCount("MEMBER", "FEMALE", "40", 1));
        assertThat(statistics.keywords(KeywordRankingType.POPULAR, DAY, DAY, 20))
            .containsExactly(new KeywordCount("엑셀", 3));
        assertThat(statistics.keywords(KeywordRankingType.ZERO_RESULT, DAY, DAY, 20))
            .containsExactly(new KeywordCount("엑셀", 1));
        assertThat(statistics.bannerClicks(DAY, DAY))
            .containsExactly(new BannerClickCount(banner, "통계 배너", false, 1));
    }

    @Test
    void trendFillsEmptyDaysAndMonthsWithZero() {
        record(UsageEventType.VISIT, "g1", "", VisitorSegment.GUEST, false);
        statistics.aggregateDay(DAY, 3, AT);

        List<UsageTrendRow> days = statistics.trend(DAY.minusDays(1), DAY.plusDays(1), UsageInterval.DAY);
        assertThat(days).extracting(UsageTrendRow::period).containsExactly("2031-01-04", "2031-01-05", "2031-01-06");
        assertThat(days).extracting(UsageTrendRow::visitors).containsExactly(0L, 1L, 0L);
        assertThat(statistics.trend(LocalDate.of(2030, 12, 1), DAY, UsageInterval.MONTH))
            .extracting(UsageTrendRow::period).containsExactly("2030-12", "2031-01");
    }

    @Test
    void usageSaltIsOneRowPerDayAndOldSaltsAreDeleted() {
        byte[] first = salts.saltFor(DAY);

        assertThat(salts.saltFor(DAY)).isEqualTo(first).hasSize(32);
        assertThat(salts.deleteSaltsBefore(DAY)).isZero();
        assertThat(salts.deleteSaltsBefore(DAY.plusDays(1))).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM tb_usage_salts WHERE salt_date = DATE '2031-01-05'")).isZero();
    }

    @Test
    void retentionDeletesOnlyOlderEvents() {
        record(UsageEventType.VISIT, "old", "", VisitorSegment.GUEST, false);

        assertThat(events.deleteEventsBefore(DAY)).isZero();
        assertThat(events.deleteEventsBefore(DAY.plusDays(1))).isGreaterThanOrEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM tb_usage_events WHERE event_date = DATE '2031-01-05'")).isZero();
    }

    private void record(UsageEventType type, String viewer, String target, VisitorSegment segment, boolean zeroResult) {
        events.record(new UsageEvent(type, DAY, viewer.repeat(64).substring(0, 64), target, segment, zeroResult, AT));
    }

    private int count(String sql) {
        return jdbcTemplate.queryForObject(sql, Integer.class);
    }

    private UUID insertUser(String gender, LocalDate birthDate) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO tb_users (id, name, gender, birth_date) VALUES (?, ?, ?, ?)",
            id, "통계 학습자", gender, birthDate);
        return id;
    }

    private UUID insertCourse() {
        UUID institution = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO tb_institutions (id, institution_code, name) VALUES (?, ?, ?)",
            institution, "use" + institution.toString().substring(0, 8), "통계 기관");
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
            INSERT INTO tb_courses (id, institution_id, course_code, title, education_type, start_date, end_date,
                                    capacity, status, classification_code)
            VALUES (?, ?, ?, '통계 강좌', 'OFFLINE', DATE '2031-01-01', DATE '2031-01-31', 10, 'RECRUITING', 'TSIX_040201')
            """, id, institution, "USE-" + id.toString().substring(0, 8));
        return id;
    }

    private UUID insertBanner() {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO tb_banners (id, title, visibility) VALUES (?, '통계 배너', 'PUBLIC')", id);
        return id;
    }
}
