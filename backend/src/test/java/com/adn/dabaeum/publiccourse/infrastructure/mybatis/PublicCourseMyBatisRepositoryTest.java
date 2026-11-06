package com.adn.dabaeum.publiccourse.infrastructure.mybatis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.adn.dabaeum.publiccourse.domain.PublicCourseRepository;
import com.adn.dabaeum.publiccourse.domain.PublicCourseTimeOfDay;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** touch 는 pgjdbc 파라미터 한도(65,535)를 넘지 않게 나눠 보낸다. 검색 조건은 SQL 파라미터로 옮긴다. */
@ExtendWith(MockitoExtension.class)
class PublicCourseMyBatisRepositoryTest {

    @Mock PublicCourseMapper mapper;

    @Test
    void touchIsSentInChunks() {
        PublicCourseMyBatisRepository repository = new PublicCourseMyBatisRepository(mapper);
        List<String> keys = IntStream.range(0, PublicCourseMyBatisRepository.TOUCH_CHUNK * 2 + 1)
            .mapToObj(index -> "k" + index).toList();
        when(mapper.touch(anyList(), any())).thenAnswer(invocation -> ((List<?>) invocation.getArgument(0)).size());

        int touched = repository.touch(keys, Instant.parse("2026-10-08T00:00:00Z"));

        assertThat(touched).isEqualTo(keys.size());
        verify(mapper, times(3)).touch(anyList(), any());
    }

    @Test
    void queryPassesReceiptDayAndTimeBounds() {
        PublicCourseMyBatisRepository repository = new PublicCourseMyBatisRepository(mapper);
        when(mapper.count(any())).thenReturn(0L);

        repository.count(query(LocalDate.of(2026, 10, 9), List.of("토", "일"), PublicCourseTimeOfDay.AFTERNOON));

        ArgumentCaptor<PublicCourseMapper.QueryParams> captor = ArgumentCaptor.forClass(PublicCourseMapper.QueryParams.class);
        verify(mapper).count(captor.capture());
        PublicCourseMapper.QueryParams params = captor.getValue();
        assertThat(params.receiptOpenOn()).isEqualTo(LocalDate.of(2026, 10, 9));
        assertThat(params.operDays()).isEqualTo("토" + PublicCourseMyBatisRepository.ARRAY_SEPARATOR + "일");
        assertThat(params.startTimeFrom()).isEqualTo(LocalTime.of(12, 0));
        assertThat(params.startTimeBefore()).isEqualTo(LocalTime.of(18, 0));
    }

    @Test
    void openEndedTimeOfDayAndNoDaysLeaveBoundsNull() {
        PublicCourseMyBatisRepository repository = new PublicCourseMyBatisRepository(mapper);
        when(mapper.count(any())).thenReturn(0L);

        repository.count(query(null, List.of(), PublicCourseTimeOfDay.EVENING));

        ArgumentCaptor<PublicCourseMapper.QueryParams> captor = ArgumentCaptor.forClass(PublicCourseMapper.QueryParams.class);
        verify(mapper).count(captor.capture());
        assertThat(captor.getValue().operDays()).isNull();
        assertThat(captor.getValue().startTimeFrom()).isEqualTo(LocalTime.of(18, 0));
        assertThat(captor.getValue().startTimeBefore()).isNull();
    }

    @Test
    void receiptStatusSortPassesReferenceDate() {
        PublicCourseMyBatisRepository repository = new PublicCourseMyBatisRepository(mapper);
        when(mapper.search(any(), org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyLong()))
            .thenReturn(List.of());

        repository.search(new PublicCourseRepository.Query(null, null, List.of(), null, null, null, null, null, null,
            List.of(), null, PublicCourseRepository.SortField.RECEIPT_STATUS, true, LocalDate.of(2026, 10, 9)), 20, 0);

        ArgumentCaptor<PublicCourseMapper.QueryParams> captor = ArgumentCaptor.forClass(PublicCourseMapper.QueryParams.class);
        verify(mapper).search(captor.capture(), org.mockito.ArgumentMatchers.eq(20), org.mockito.ArgumentMatchers.eq(0L));
        assertThat(captor.getValue().receiptStatusOn()).isEqualTo(LocalDate.of(2026, 10, 9));
    }

    @Test
    void countByRegionReturnsCodeToCount() {
        PublicCourseMyBatisRepository repository = new PublicCourseMyBatisRepository(mapper);
        when(mapper.countByRegion(any())).thenReturn(List.of(
            new PublicCourseMapper.RegionCountRow("26", 416L), new PublicCourseMapper.RegionCountRow("29", 39L)));

        assertThat(repository.countByRegion(query(null, List.of(), null)))
            .containsExactlyInAnyOrderEntriesOf(Map.of("26", 416L, "29", 39L));
    }

    @Test
    void emptyTouchDoesNotCallMapper() {
        PublicCourseMyBatisRepository repository = new PublicCourseMyBatisRepository(mapper);

        assertThat(repository.touch(List.of(), Instant.parse("2026-10-08T00:00:00Z"))).isZero();
        verify(mapper, never()).touch(anyList(), any());
    }

    private static PublicCourseRepository.Query query(
        LocalDate receiptOpenOn, List<String> operDays, PublicCourseTimeOfDay timeOfDay
    ) {
        return new PublicCourseRepository.Query(null, null, List.of(), null, null, null, null, null, receiptOpenOn,
            operDays, timeOfDay, PublicCourseRepository.SortField.EDC_START_DATE, true, null);
    }
}
