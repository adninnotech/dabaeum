package com.adn.dabaeum.usage.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.adn.dabaeum.usage.domain.UsageStatisticsRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** docs/specs/usage-statistics.md §5 — 마지막 집계 다음 날부터 어제까지, 처음엔 366일 전부터. */
@ExtendWith(MockitoExtension.class)
class UsageAggregatorTest {

    // 서울 2026-10-30 00:20
    private static final Instant NOW = Instant.parse("2026-10-29T15:20:00Z");
    private static final LocalDate YESTERDAY = LocalDate.of(2026, 10, 29);

    @Mock UsageStatisticsRepository repository;

    @Test
    void fillsEveryDayAfterTheLastAggregatedUpToYesterday() {
        when(repository.lastAggregatedDay()).thenReturn(Optional.of(YESTERDAY.minusDays(3)));

        int days = new UsageAggregator(repository).aggregatePending(NOW);

        assertThat(days).isEqualTo(3);
        verify(repository).aggregateDay(YESTERDAY.minusDays(2), UsageAggregator.KEYWORD_MIN_SEARCHERS, NOW);
        verify(repository).aggregateDay(YESTERDAY.minusDays(1), UsageAggregator.KEYWORD_MIN_SEARCHERS, NOW);
        verify(repository).aggregateDay(YESTERDAY, UsageAggregator.KEYWORD_MIN_SEARCHERS, NOW);
    }

    @Test
    void doesNothingWhenYesterdayIsDone() {
        when(repository.lastAggregatedDay()).thenReturn(Optional.of(YESTERDAY));

        assertThat(new UsageAggregator(repository).aggregatePending(NOW)).isZero();
        verify(repository, never()).aggregateDay(any(), anyInt(), any());
    }

    @Test
    void firstRunAndLongGapStartAtRawRetentionWindow() {
        when(repository.lastAggregatedDay()).thenReturn(Optional.empty());

        assertThat(new UsageAggregator(repository).aggregatePending(NOW)).isEqualTo(366);
        verify(repository).aggregateDay(eq(LocalDate.of(2025, 10, 29)), anyInt(), any());
        verify(repository, times(366)).aggregateDay(any(), anyInt(), any());
    }
}
