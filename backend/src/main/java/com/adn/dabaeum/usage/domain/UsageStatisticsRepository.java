package com.adn.dabaeum.usage.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** 일별 집계와 관리자 조회 (docs/specs/usage-statistics.md §5, §6). 날짜는 모두 서울, [from, to] 포함. */
public interface UsageStatisticsRepository {

    Optional<LocalDate> lastAggregatedDay();

    /** 그날 집계를 지우고 다시 넣는다. 검색어는 keywordMinSearchers 명 이상만 남긴다. */
    void aggregateDay(LocalDate day, int keywordMinSearchers, Instant at);

    List<UsageTrendRow> trend(LocalDate from, LocalDate to, UsageInterval interval);

    UsageCounts totals(LocalDate from, LocalDate to);

    List<SegmentCount> segments(UsageMetric metric, LocalDate from, LocalDate to);

    List<KeywordCount> keywords(KeywordRankingType type, LocalDate from, LocalDate to, int limit);

    List<BannerClickCount> bannerClicks(LocalDate from, LocalDate to);
}
