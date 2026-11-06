package com.adn.dabaeum.usage.application;

import com.adn.dabaeum.usage.domain.UsageStatisticsRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;

/**
 * 일별 집계 (docs/specs/usage-statistics.md §5). 마지막으로 집계한 다음 날부터 어제까지 하루씩 다시 쓴다.
 * 원문을 1년 보존하므로 366일보다 앞은 볼 수 없다 — 처음 돌거나 오래 멈췄으면 거기서 시작한다.
 */
public class UsageAggregator {

    /** 검색어 순위에 남기는 하루 최소 검색자 수(§3, §6.3). */
    public static final int KEYWORD_MIN_SEARCHERS = 3;
    static final int RAW_WINDOW_DAYS = 366;

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final UsageStatisticsRepository repository;

    public UsageAggregator(UsageStatisticsRepository repository) {
        this.repository = Objects.requireNonNull(repository);
    }

    /** 집계한 날 수를 돌려준다. */
    public int aggregatePending(Instant now) {
        LocalDate today = now.atZone(SEOUL).toLocalDate();
        LocalDate earliest = today.minusDays(RAW_WINDOW_DAYS);
        LocalDate start = repository.lastAggregatedDay()
            .map(day -> day.plusDays(1))
            .filter(day -> day.isAfter(earliest))
            .orElse(earliest);
        int days = 0;
        for (LocalDate day = start; day.isBefore(today); day = day.plusDays(1)) {
            repository.aggregateDay(day, KEYWORD_MIN_SEARCHERS, now);
            days++;
        }
        return days;
    }
}
