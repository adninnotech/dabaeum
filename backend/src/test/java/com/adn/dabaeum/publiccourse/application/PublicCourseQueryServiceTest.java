package com.adn.dabaeum.publiccourse.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.adn.dabaeum.common.api.ApiErrorCode;
import com.adn.dabaeum.common.api.ApiException;
import com.adn.dabaeum.publiccourse.domain.PublicCourseMethod;
import com.adn.dabaeum.publiccourse.domain.PublicCourseRepository;
import com.adn.dabaeum.publiccourse.domain.PublicCourseRepository.SortField;
import com.adn.dabaeum.publiccourse.domain.PublicCourseSyncRunRepository;
import com.adn.dabaeum.publiccourse.domain.PublicCourseTimeOfDay;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** 설계서 §5.1·§5.2 — 기본 종료 제외·접수 중(서울 날짜), 요일·시간대, 구·군 건수, 파라미터 검증, 404. */
@ExtendWith(MockitoExtension.class)
class PublicCourseQueryServiceTest {

    /** 서울은 이미 10월 9일, UTC 로는 10월 8일. */
    private static final Instant NOW = Instant.parse("2026-10-08T16:00:00Z");

    @Mock PublicCourseRepository courses;
    @Mock PublicCourseSyncRunRepository runs;

    private PublicCourseQueryService service;

    @BeforeEach
    void setUp() {
        service = new PublicCourseQueryService(courses, runs, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void defaultListExcludesCoursesEndedBeforeTodayInSeoul() {
        when(courses.count(any())).thenReturn(0L);

        PublicCourseQueryService.Page<?> page = service.search(query(null, null, null, false, "edcStartDate,asc"));

        ArgumentCaptor<PublicCourseRepository.Query> captor = ArgumentCaptor.forClass(PublicCourseRepository.Query.class);
        verify(courses).count(captor.capture());
        assertThat(captor.getValue().endedFrom()).isEqualTo(LocalDate.of(2026, 10, 9));
        assertThat(captor.getValue().sortField()).isEqualTo(SortField.EDC_START_DATE);
        assertThat(captor.getValue().ascending()).isTrue();
        assertThat(page.totalPages()).isZero();
        verify(courses, never()).search(any(), anyInt(), anyLong());
    }

    @Test
    void includeEndedDropsEndDateCondition() {
        when(courses.count(any())).thenReturn(0L);

        service.search(query(null, null, null, true, "lectureName,desc"));

        ArgumentCaptor<PublicCourseRepository.Query> captor = ArgumentCaptor.forClass(PublicCourseRepository.Query.class);
        verify(courses).count(captor.capture());
        assertThat(captor.getValue().endedFrom()).isNull();
        assertThat(captor.getValue().sortField()).isEqualTo(SortField.LECTURE_NAME);
        assertThat(captor.getValue().ascending()).isFalse();
    }

    @Test
    void passesNormalizedFilters() {
        when(courses.count(any())).thenReturn(45L);
        when(courses.search(any(), anyInt(), anyLong())).thenReturn(List.of());

        PublicCourseQueryService.Page<?> page = service.search(new PublicCourseQueryService.ListQuery(
            new PublicCourseQueryService.Filter(" 요가_100% ", "HYBRID", List.of(" 어르신 ", "어르신", ""), "26", true,
                null, null, false, false, List.of(), null),
            2, 20, "receiptEndDate,asc"));

        ArgumentCaptor<PublicCourseRepository.Query> captor = ArgumentCaptor.forClass(PublicCourseRepository.Query.class);
        verify(courses).search(captor.capture(), org.mockito.ArgumentMatchers.eq(20),
            org.mockito.ArgumentMatchers.eq(40L));
        PublicCourseRepository.Query criteria = captor.getValue();
        assertThat(criteria.keyword()).isEqualTo("요가\\_100\\%");
        assertThat(criteria.method()).isEqualTo(PublicCourseMethod.HYBRID);
        assertThat(criteria.targetTypes()).containsExactly("어르신");
        assertThat(criteria.regionCode()).isEqualTo("26");
        assertThat(criteria.free()).isTrue();
        assertThat(page.totalPages()).isEqualTo(3);
    }

    @ParameterizedTest
    @CsvSource(delimiter = ';', value = {
        "OTHER;;edcStartDate,asc;method",
        "offline;;edcStartDate,asc;method",
        ";99;edcStartDate,asc;regionCode",
        ";00;edcStartDate,asc;regionCode",
        ";;title,asc;sort",
        ";;edcStartDate,up;sort",
        ";;edcStartDate;sort",
        ";;receiptStatus,desc;sort"
    })
    void rejectsInvalidParameters(String method, String region, String sort, String detail) {
        assertThatThrownBy(() -> service.search(query(method, region, null, false, sort)))
            .isInstanceOfSatisfying(ApiException.class, exception -> {
                assertThat(exception.code()).isEqualTo(ApiErrorCode.BAD_REQUEST);
                assertThat(exception.details()).containsExactly(detail);
            });
    }

    @Test
    void rejectsReversedPeriodAndPageBounds() {
        assertThatThrownBy(() -> service.search(new PublicCourseQueryService.ListQuery(
            new PublicCourseQueryService.Filter(null, null, null, null, null, LocalDate.of(2026, 12, 1),
                LocalDate.of(2026, 11, 1), false, false, List.of(), null),
            0, 20, "edcStartDate,asc")))
            .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.search(new PublicCourseQueryService.ListQuery(emptyFilter(), 0, 101,
            "edcStartDate,asc")))
            .isInstanceOf(ApiException.class);
    }

    @Test
    void receiptOpenUsesTodayInSeoul() {
        when(courses.count(any())).thenReturn(0L);

        service.search(new PublicCourseQueryService.ListQuery(
            new PublicCourseQueryService.Filter(null, null, null, null, null, null, null, false, true, List.of(), null),
            0, 20, "receiptEndDate,asc"));

        ArgumentCaptor<PublicCourseRepository.Query> captor = ArgumentCaptor.forClass(PublicCourseRepository.Query.class);
        verify(courses).count(captor.capture());
        assertThat(captor.getValue().receiptOpenOn()).isEqualTo(LocalDate.of(2026, 10, 9));
    }

    @Test
    void receiptStatusSortUsesTodayInSeoul() {
        when(courses.count(any())).thenReturn(0L);

        service.search(query(null, null, null, false, "receiptStatus,asc"));

        ArgumentCaptor<PublicCourseRepository.Query> captor = ArgumentCaptor.forClass(PublicCourseRepository.Query.class);
        verify(courses).count(captor.capture());
        assertThat(captor.getValue().sortField()).isEqualTo(SortField.RECEIPT_STATUS);
        assertThat(captor.getValue().receiptStatusOn()).isEqualTo(LocalDate.of(2026, 10, 9));
        assertThat(captor.getValue().receiptOpenOn()).isNull();
    }

    @Test
    void otherSortsCarryNoReceiptStatusDate() {
        when(courses.count(any())).thenReturn(0L);

        service.search(query(null, null, null, false, "receiptEndDate,asc"));

        ArgumentCaptor<PublicCourseRepository.Query> captor = ArgumentCaptor.forClass(PublicCourseRepository.Query.class);
        verify(courses).count(captor.capture());
        assertThat(captor.getValue().receiptStatusOn()).isNull();
    }

    @Test
    void receiptOpenFalseAddsNoReceiptCondition() {
        when(courses.count(any())).thenReturn(0L);

        service.search(query(null, null, null, false, "edcStartDate,asc"));

        ArgumentCaptor<PublicCourseRepository.Query> captor = ArgumentCaptor.forClass(PublicCourseRepository.Query.class);
        verify(courses).count(captor.capture());
        assertThat(captor.getValue().receiptOpenOn()).isNull();
        assertThat(captor.getValue().operDays()).isEmpty();
        assertThat(captor.getValue().timeOfDay()).isNull();
    }

    @Test
    void passesOperDaysAndTimeOfDay() {
        when(courses.count(any())).thenReturn(0L);

        service.search(new PublicCourseQueryService.ListQuery(
            new PublicCourseQueryService.Filter(null, null, null, null, null, null, null, false, false,
                List.of(" 토", "일", "토", ""), " EVENING "),
            0, 20, "edcStartDate,asc"));

        ArgumentCaptor<PublicCourseRepository.Query> captor = ArgumentCaptor.forClass(PublicCourseRepository.Query.class);
        verify(courses).count(captor.capture());
        assertThat(captor.getValue().operDays()).containsExactly("토", "일");
        assertThat(captor.getValue().timeOfDay()).isEqualTo(PublicCourseTimeOfDay.EVENING);
    }

    @ParameterizedTest
    @CsvSource(delimiter = ';', value = {
        "주말;;operDay",
        "토요일;;operDay",
        ";evening;timeOfDay",
        ";NIGHT;timeOfDay"
    })
    void rejectsInvalidDayOrTime(String operDay, String timeOfDay, String detail) {
        PublicCourseQueryService.Filter filter = new PublicCourseQueryService.Filter(null, null, null, null, null,
            null, null, false, false, operDay == null ? List.of() : List.of(operDay), timeOfDay);

        assertThatThrownBy(() -> service.search(new PublicCourseQueryService.ListQuery(filter, 0, 20,
            "edcStartDate,asc")))
            .isInstanceOfSatisfying(ApiException.class,
                exception -> assertThat(exception.details()).containsExactly(detail));
    }

    @Test
    void regionFacetsListEveryDaeguRegionInOrderAndIgnoreRegionFilter() {
        when(courses.count(any())).thenReturn(470L);
        when(courses.countByRegion(any())).thenReturn(Map.of("26", 416L, "29", 39L, "14", 5L));

        PublicCourseQueryService.RegionFacets facets = service.regionFacets(new PublicCourseQueryService.Filter(
            "요가", null, null, "26", true, null, null, false, true, List.of(), null));

        ArgumentCaptor<PublicCourseRepository.Query> captor = ArgumentCaptor.forClass(PublicCourseRepository.Query.class);
        verify(courses).countByRegion(captor.capture());
        assertThat(captor.getValue().regionCode()).isNull();
        assertThat(captor.getValue().keyword()).isEqualTo("요가");
        assertThat(captor.getValue().free()).isTrue();
        assertThat(captor.getValue().receiptOpenOn()).isEqualTo(LocalDate.of(2026, 10, 9));
        assertThat(captor.getValue().endedFrom()).isEqualTo(LocalDate.of(2026, 10, 9));
        assertThat(facets.total()).isEqualTo(470L);
        assertThat(facets.regions())
            .extracting(PublicCourseQueryService.RegionCount::regionCode)
            .containsExactly("11", "14", "17", "20", "23", "26", "29", "71", "72");
        assertThat(facets.regions())
            .extracting(PublicCourseQueryService.RegionCount::count)
            .containsExactly(0L, 5L, 0L, 0L, 0L, 416L, 39L, 0L, 0L);
    }

    @Test
    void unknownCourseIsNotFound() {
        UUID id = UUID.randomUUID();
        when(courses.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.detail(id))
            .isInstanceOfSatisfying(ApiException.class,
                exception -> assertThat(exception.code()).isEqualTo(ApiErrorCode.PUBLIC_COURSE_NOT_FOUND));
    }

    @Test
    void unknownRunIsNotFound() {
        UUID id = UUID.randomUUID();
        when(runs.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.run(id))
            .isInstanceOfSatisfying(ApiException.class,
                exception -> assertThat(exception.code()).isEqualTo(ApiErrorCode.PUBLIC_COURSE_SYNC_RUN_NOT_FOUND));
    }

    private static PublicCourseQueryService.ListQuery query(
        String method, String region, List<String> targetTypes, boolean includeEnded, String sort
    ) {
        return new PublicCourseQueryService.ListQuery(
            new PublicCourseQueryService.Filter(null, method, targetTypes, region, null, null, null, includeEnded,
                false, List.of(), null),
            0, 20, sort);
    }

    private static PublicCourseQueryService.Filter emptyFilter() {
        return new PublicCourseQueryService.Filter(null, null, null, null, null, null, null, false, false, List.of(),
            null);
    }
}
