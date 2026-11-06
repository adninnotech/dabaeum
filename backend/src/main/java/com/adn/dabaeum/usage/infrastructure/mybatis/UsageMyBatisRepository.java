package com.adn.dabaeum.usage.infrastructure.mybatis;

import com.adn.dabaeum.usage.domain.BannerClickCount;
import com.adn.dabaeum.usage.domain.KeywordCount;
import com.adn.dabaeum.usage.domain.KeywordRankingType;
import com.adn.dabaeum.usage.domain.MemberProfile;
import com.adn.dabaeum.usage.domain.SegmentCount;
import com.adn.dabaeum.usage.domain.UsageCounts;
import com.adn.dabaeum.usage.domain.UsageEvent;
import com.adn.dabaeum.usage.domain.UsageEventRepository;
import com.adn.dabaeum.usage.domain.UsageInterval;
import com.adn.dabaeum.usage.domain.UsageMetric;
import com.adn.dabaeum.usage.domain.UsageSaltRepository;
import com.adn.dabaeum.usage.domain.UsageStatisticsRepository;
import com.adn.dabaeum.usage.domain.UsageTrendRow;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile({"local", "dev"})
public class UsageMyBatisRepository implements UsageEventRepository, UsageStatisticsRepository, UsageSaltRepository {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final int SALT_BYTES = 32;

    private final UsageMapper mapper;
    private final SecureRandom random = new SecureRandom();

    public UsageMyBatisRepository(UsageMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<MemberProfile> findMember(UUID userId) {
        return Optional.ofNullable(mapper.selectMember(userId));
    }

    @Override
    public void record(UsageEvent event) {
        mapper.insertEventIfAbsent(event);
    }

    @Override
    public int deleteEventsBefore(LocalDate date) {
        return mapper.deleteEventsBefore(date);
    }

    @Override
    public byte[] saltFor(LocalDate date) {
        byte[] candidate = new byte[SALT_BYTES];
        random.nextBytes(candidate);
        // 먼저 넣은 서버의 salt 가 이긴다. 그 뒤 모두 같은 행을 읽는다.
        mapper.insertSaltIfAbsent(date, candidate);
        return mapper.selectSalt(date).salt();
    }

    @Override
    public int deleteSaltsBefore(LocalDate date) {
        return mapper.deleteSaltsBefore(date);
    }

    @Override
    public Optional<LocalDate> lastAggregatedDay() {
        return Optional.ofNullable(mapper.selectLastAggregatedDay());
    }

    @Override
    public void aggregateDay(LocalDate day, int keywordMinSearchers, Instant at) {
        mapper.deleteDailyStats(day);
        mapper.insertDailyStats(day, day.atStartOfDay(SEOUL).toInstant(), day.plusDays(1).atStartOfDay(SEOUL).toInstant());
        mapper.deleteKeywordStats(day);
        mapper.insertKeywordStats(day, keywordMinSearchers, UsageEvent.MASKED_KEYWORD);
        mapper.markAggregated(day, at);
    }

    @Override
    public List<UsageTrendRow> trend(LocalDate from, LocalDate to, UsageInterval interval) {
        return mapper.selectTrend(from, to, interval == UsageInterval.MONTH);
    }

    @Override
    public UsageCounts totals(LocalDate from, LocalDate to) {
        return mapper.selectTotals(from, to);
    }

    @Override
    public List<SegmentCount> segments(UsageMetric metric, LocalDate from, LocalDate to) {
        return mapper.selectSegments(metric.name(), from, to);
    }

    @Override
    public List<KeywordCount> keywords(KeywordRankingType type, LocalDate from, LocalDate to, int limit) {
        return mapper.selectKeywords(type == KeywordRankingType.ZERO_RESULT, from, to, limit);
    }

    @Override
    public List<BannerClickCount> bannerClicks(LocalDate from, LocalDate to) {
        return mapper.selectBannerClicks(from, to);
    }
}
