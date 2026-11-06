package com.adn.dabaeum.usage.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.adn.dabaeum.common.api.ApiErrorCode;
import com.adn.dabaeum.common.api.ApiException;
import com.adn.dabaeum.usage.domain.KeywordRankingType;
import com.adn.dabaeum.usage.domain.SegmentCount;
import com.adn.dabaeum.usage.domain.UsageCounts;
import com.adn.dabaeum.usage.domain.UsageInterval;
import com.adn.dabaeum.usage.domain.UsageMetric;
import com.adn.dabaeum.usage.domain.UsageStatisticsRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** docs/specs/usage-statistics.md §6 — 기본 어제까지 30일, 오늘은 집계 전이라 막는다, 앞 기간은 같은 길이. */
@ExtendWith(MockitoExtension.class)
class UsageStatisticsServiceTest {

    // 서울 2026-10-30 00:30
    private static final Instant NOW = Instant.parse("2026-10-29T15:30:00Z");
    private static final LocalDate YESTERDAY = LocalDate.of(2026, 10, 29);

    @Mock UsageStatisticsRepository repository;
    private UsageStatisticsService service;

    @BeforeEach
    void setUp() {
        service = new UsageStatisticsService(repository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void defaultPeriodIsLastThirtyDaysEndingYesterdayAndPreviousHasSameLength() {
        when(repository.totals(LocalDate.of(2026, 9, 30), YESTERDAY)).thenReturn(UsageCounts.ZERO);
        when(repository.totals(LocalDate.of(2026, 8, 31), LocalDate.of(2026, 9, 29))).thenReturn(UsageCounts.ZERO);

        UsageStatisticsService.Summary summary = service.summary(null, null, null);

        assertThat(summary.from()).isEqualTo(LocalDate.of(2026, 9, 30));
        assertThat(summary.to()).isEqualTo(YESTERDAY);
        assertThat(summary.interval()).isEqualTo(UsageInterval.DAY);
        assertThat(summary.previousFrom()).isEqualTo(LocalDate.of(2026, 8, 31));
        assertThat(summary.previousTo()).isEqualTo(LocalDate.of(2026, 9, 29));
        verify(repository).trend(LocalDate.of(2026, 9, 30), YESTERDAY, UsageInterval.DAY);
    }

    @Test
    void periodEndsAtTheLastAggregatedDayWhenTheBatchIsBehind() {
        LocalDate last = YESTERDAY.minusDays(2);
        when(repository.lastAggregatedDay()).thenReturn(java.util.Optional.of(last));

        assertThat(service.banners(null, null).to()).isEqualTo(last);
        assertThatThrownBy(() -> service.banners(null, YESTERDAY))
            .isInstanceOfSatisfying(ApiException.class, exception -> assertThat(exception.details()).containsExactly("to"));
    }

    @Test
    void todayIsNotAggregatedYet() {
        assertThatThrownBy(() -> service.summary(null, YESTERDAY.plusDays(1), UsageInterval.DAY))
            .isInstanceOfSatisfying(ApiException.class, exception -> {
                assertThat(exception.code()).isEqualTo(ApiErrorCode.VALIDATION_FAILED);
                assertThat(exception.details()).containsExactly("to");
            });
    }

    @Test
    void fromAfterToAndDailyRangeOverAYearAreRejected() {
        assertThatThrownBy(() -> service.summary(YESTERDAY, YESTERDAY.minusDays(1), null))
            .isInstanceOfSatisfying(ApiException.class, exception -> assertThat(exception.details()).containsExactly("from"));
        assertThatThrownBy(() -> service.summary(YESTERDAY.minusDays(366), YESTERDAY, UsageInterval.DAY))
            .isInstanceOfSatisfying(ApiException.class, exception -> assertThat(exception.details()).containsExactly("interval"));
        assertThatThrownBy(() -> service.summary(YESTERDAY.minusYears(6), YESTERDAY, UsageInterval.MONTH))
            .isInstanceOfSatisfying(ApiException.class, exception -> assertThat(exception.details()).containsExactly("to"));
    }

    @Test
    void demographicsSplitGuestsAndCountMembersByFixedOrder() {
        when(repository.segments(UsageMetric.VISIT, LocalDate.of(2026, 9, 30), YESTERDAY)).thenReturn(List.of(
            new SegmentCount("GUEST", "UNKNOWN", "UNKNOWN", 7),
            new SegmentCount("MEMBER", "FEMALE", "30", 3),
            new SegmentCount("MEMBER", "MALE", "30", 2),
            new SegmentCount("MEMBER", "FEMALE", "UNKNOWN", 1)));

        UsageStatisticsService.Demographics result = service.demographics(UsageMetric.VISIT, null, null);

        assertThat(result.total()).isEqualTo(13);
        assertThat(result.members()).isEqualTo(6);
        assertThat(result.guests()).isEqualTo(7);
        assertThat(result.genders()).extracting(UsageStatisticsService.Bucket::key)
            .containsExactly("MALE", "FEMALE", "UNDISCLOSED", "UNKNOWN");
        assertThat(result.genders()).extracting(UsageStatisticsService.Bucket::count).containsExactly(2L, 4L, 0L, 0L);
        assertThat(result.genders().get(0).label()).isEqualTo("남성");
        assertThat(result.ageBands()).extracting(UsageStatisticsService.Bucket::key)
            .containsExactly("10", "20", "30", "40", "50", "60", "70", "UNKNOWN");
        assertThat(result.ageBands()).extracting(UsageStatisticsService.Bucket::count)
            .containsExactly(0L, 0L, 5L, 0L, 0L, 0L, 0L, 1L);
        assertThat(result.ageBands().get(0).label()).isEqualTo("10대 이하");
    }

    @Test
    void keywordLimitIsBounded() {
        assertThatThrownBy(() -> service.keywords(KeywordRankingType.POPULAR, null, null, 0))
            .isInstanceOfSatisfying(ApiException.class, exception -> assertThat(exception.code()).isEqualTo(ApiErrorCode.BAD_REQUEST));
        assertThatThrownBy(() -> service.keywords(KeywordRankingType.POPULAR, null, null, 101))
            .isInstanceOf(ApiException.class);

        service.keywords(KeywordRankingType.ZERO_RESULT, null, null, null);

        verify(repository).keywords(KeywordRankingType.ZERO_RESULT, LocalDate.of(2026, 9, 30), YESTERDAY, 20);
    }
}
