package com.adn.dabaeum.statistics.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

import com.adn.dabaeum.common.api.ApiErrorCode;
import com.adn.dabaeum.common.api.ApiException;
import com.adn.dabaeum.statistics.domain.EnrollmentStatGroup;
import com.adn.dabaeum.statistics.domain.EnrollmentStatRow;
import com.adn.dabaeum.statistics.domain.StatisticsRepository;
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

/** 설계서 §9 — 기간 기본값·상한, 축별 표시 이름. */
@ExtendWith(MockitoExtension.class)
class StatisticsServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-28T03:00:00Z");

    @Mock StatisticsRepository repository;
    private StatisticsService service;

    @BeforeEach
    void setUp() {
        service = new StatisticsService(repository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void defaultPeriodIsThisYearUntilTodayInSeoul() {
        when(repository.enrollments(eq(EnrollmentStatGroup.AGE_BAND), eq(Instant.parse("2025-12-31T15:00:00Z")),
            eq(Instant.parse("2026-09-28T15:00:00Z")), isNull(), isNull(), anyInt()))
            .thenReturn(List.of(new EnrollmentStatRow("30", null, 3, 2, 1)));

        StatisticsService.EnrollmentStatistics result =
            service.enrollments(EnrollmentStatGroup.AGE_BAND, null, null, null, null, null);

        assertThat(result.period().from()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(result.period().to()).isEqualTo(LocalDate.of(2026, 9, 28));
        assertThat(result.rows()).singleElement().satisfies(row -> assertThat(row.label()).isEqualTo("30대"));
    }

    @Test
    void periodLongerThanFiveYearsIsRejected() {
        assertThatThrownBy(() -> service.enrollments(EnrollmentStatGroup.YEAR,
            LocalDate.of(2020, 1, 1), LocalDate.of(2025, 1, 2), null, null, null))
            .isInstanceOfSatisfying(ApiException.class,
                exception -> assertThat(exception.code()).isEqualTo(ApiErrorCode.VALIDATION_FAILED));
    }

    @Test
    void malformedClassificationIsBadRequest() {
        assertThatThrownBy(() -> service.enrollments(EnrollmentStatGroup.CONTENT, null, null, "문화", null, null))
            .isInstanceOfSatisfying(ApiException.class,
                exception -> assertThat(exception.code()).isEqualTo(ApiErrorCode.BAD_REQUEST));
    }

    @Test
    void ageBandLabels() {
        assertThat(StatisticsService.label(EnrollmentStatGroup.AGE_BAND, "10")).isEqualTo("10대 이하");
        assertThat(StatisticsService.label(EnrollmentStatGroup.AGE_BAND, "40")).isEqualTo("40대");
        assertThat(StatisticsService.label(EnrollmentStatGroup.AGE_BAND, "70")).isEqualTo("70대 이상");
        assertThat(StatisticsService.label(EnrollmentStatGroup.AGE_BAND, "UNKNOWN")).isEqualTo("미상");
    }

    @Test
    void genderLabels() {
        assertThat(StatisticsService.label(EnrollmentStatGroup.GENDER, "MALE")).isEqualTo("남성");
        assertThat(StatisticsService.label(EnrollmentStatGroup.GENDER, "FEMALE")).isEqualTo("여성");
        assertThat(StatisticsService.label(EnrollmentStatGroup.GENDER, "UNDISCLOSED")).isEqualTo("밝히지 않음");
        assertThat(StatisticsService.label(EnrollmentStatGroup.GENDER, "UNKNOWN")).isEqualTo("미상");
    }
}
