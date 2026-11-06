package com.adn.dabaeum.usage.application;

import com.adn.dabaeum.common.api.ApiErrorCode;
import com.adn.dabaeum.common.api.ApiException;
import com.adn.dabaeum.statistics.application.StatisticsService;
import com.adn.dabaeum.statistics.domain.EnrollmentStatGroup;
import com.adn.dabaeum.usage.domain.BannerClickCount;
import com.adn.dabaeum.usage.domain.KeywordCount;
import com.adn.dabaeum.usage.domain.KeywordRankingType;
import com.adn.dabaeum.usage.domain.SegmentCount;
import com.adn.dabaeum.usage.domain.UsageCounts;
import com.adn.dabaeum.usage.domain.UsageInterval;
import com.adn.dabaeum.usage.domain.UsageMetric;
import com.adn.dabaeum.usage.domain.UsageStatisticsRepository;
import com.adn.dabaeum.usage.domain.UsageTrendRow;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 이용 통계 조회 (docs/specs/usage-statistics.md §6). 일별 집계만 읽으므로 오늘은 아직 없다.
 * 기간은 서울 날짜 [from, to], 기본 마지막 집계일(보통 어제)까지 30일, 최대 5년(일별은 366일).
 */
@Service
@Profile({"local", "dev"})
public class UsageStatisticsService {

    static final int DEFAULT_DAYS = 30;
    static final int MAX_YEARS = 5;
    static final int MAX_DAILY_DAYS = 366;
    static final int DEFAULT_LIMIT = 20;
    static final int MAX_LIMIT = 100;

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final List<String> GENDERS = List.of("MALE", "FEMALE", "UNDISCLOSED", "UNKNOWN");
    private static final List<String> AGE_BANDS = List.of("10", "20", "30", "40", "50", "60", "70", "UNKNOWN");
    private static final String MEMBER = "MEMBER";

    private final UsageStatisticsRepository repository;
    private final Clock clock;

    public UsageStatisticsService(UsageStatisticsRepository repository, Clock clock) {
        this.repository = Objects.requireNonNull(repository);
        this.clock = Objects.requireNonNull(clock);
    }

    public record Period(LocalDate from, LocalDate to) {

        long days() {
            return ChronoUnit.DAYS.between(from, to) + 1;
        }

        Period previous() {
            LocalDate end = from.minusDays(1);
            return new Period(end.minusDays(days() - 1), end);
        }
    }

    public record Summary(
        LocalDate from, LocalDate to, UsageInterval interval, UsageCounts totals,
        LocalDate previousFrom, LocalDate previousTo, UsageCounts previousTotals, List<UsageTrendRow> rows
    ) {
    }

    public record Bucket(String key, String label, long count) {
    }

    public record Demographics(
        UsageMetric metric, LocalDate from, LocalDate to, long total, long members, long guests,
        List<Bucket> genders, List<Bucket> ageBands
    ) {
    }

    public record KeywordRanking(KeywordRankingType type, LocalDate from, LocalDate to, List<KeywordCount> rows) {
    }

    public record BannerClicks(LocalDate from, LocalDate to, List<BannerClickCount> rows) {
    }

    @Transactional(readOnly = true)
    public Summary summary(LocalDate from, LocalDate to, UsageInterval interval) {
        UsageInterval unit = interval == null ? UsageInterval.DAY : interval;
        Period period = period(from, to);
        if (unit == UsageInterval.DAY && period.days() > MAX_DAILY_DAYS) {
            throw validation("interval");
        }
        Period previous = period.previous();
        return new Summary(period.from(), period.to(), unit, repository.totals(period.from(), period.to()),
            previous.from(), previous.to(), repository.totals(previous.from(), previous.to()),
            repository.trend(period.from(), period.to(), unit));
    }

    @Transactional(readOnly = true)
    public Demographics demographics(UsageMetric metric, LocalDate from, LocalDate to) {
        Objects.requireNonNull(metric, "metric");
        Period period = period(from, to);
        List<SegmentCount> segments = repository.segments(metric, period.from(), period.to());
        List<SegmentCount> members = segments.stream().filter(segment -> MEMBER.equals(segment.memberType())).toList();
        long total = sum(segments);
        long memberCount = sum(members);
        return new Demographics(metric, period.from(), period.to(), total, memberCount, total - memberCount,
            buckets(GENDERS, members, SegmentCount::gender, EnrollmentStatGroup.GENDER),
            buckets(AGE_BANDS, members, SegmentCount::ageBand, EnrollmentStatGroup.AGE_BAND));
    }

    @Transactional(readOnly = true)
    public KeywordRanking keywords(KeywordRankingType type, LocalDate from, LocalDate to, Integer limit) {
        Objects.requireNonNull(type, "type");
        int size = limit == null ? DEFAULT_LIMIT : limit;
        if (size < 1 || size > MAX_LIMIT) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ApiErrorCode.BAD_REQUEST, "Invalid statistics query",
                List.of("limit"));
        }
        Period period = period(from, to);
        return new KeywordRanking(type, period.from(), period.to(),
            repository.keywords(type, period.from(), period.to(), size));
    }

    @Transactional(readOnly = true)
    public BannerClicks banners(LocalDate from, LocalDate to) {
        Period period = period(from, to);
        return new BannerClicks(period.from(), period.to(), repository.bannerClicks(period.from(), period.to()));
    }

    private static List<Bucket> buckets(
        List<String> keys, List<SegmentCount> members, Function<SegmentCount, String> axis, EnrollmentStatGroup labels
    ) {
        return keys.stream()
            .map(key -> new Bucket(key, StatisticsService.label(labels, key),
                sum(members.stream().filter(segment -> key.equals(axis.apply(segment))).toList())))
            .toList();
    }

    private static long sum(List<SegmentCount> segments) {
        return segments.stream().mapToLong(SegmentCount::count).sum();
    }

    private Period period(LocalDate from, LocalDate to) {
        // 집계가 밀렸으면(배포 직후·배치 실패) 마지막 집계일까지만 연다. 안 그러면 빈 날이 진짜 0 처럼 보인다(§5).
        LocalDate yesterday = clock.instant().atZone(SEOUL).toLocalDate().minusDays(1);
        LocalDate limit = repository.lastAggregatedDay().filter(day -> day.isBefore(yesterday)).orElse(yesterday);
        LocalDate end = to == null ? limit : to;
        LocalDate start = from == null ? end.minusDays(DEFAULT_DAYS - 1) : from;
        if (end.isAfter(limit)) {
            throw validation("to");
        }
        if (start.isAfter(end)) {
            throw validation("from");
        }
        if (start.plusYears(MAX_YEARS).isBefore(end)) {
            throw validation("to");
        }
        return new Period(start, end);
    }

    private static ApiException validation(String field) {
        return new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, ApiErrorCode.VALIDATION_FAILED,
            "Request validation failed", List.of(field));
    }
}
