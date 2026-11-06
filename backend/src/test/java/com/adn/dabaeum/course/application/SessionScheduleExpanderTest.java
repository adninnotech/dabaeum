package com.adn.dabaeum.course.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SessionScheduleExpanderTest {

    @Test
    void 매주_목요일_12회를_펼친다() {
        List<SessionDraft> drafts = SessionScheduleExpander.expand(new RepeatRule(
            LocalDate.of(2026, 11, 5),      // 목요일
            Set.of(DayOfWeek.THURSDAY),
            LocalTime.of(14, 0), LocalTime.of(16, 0),
            12));

        assertThat(drafts).hasSize(12);
        assertThat(drafts.get(0).sessionNo()).isEqualTo(1);
        assertThat(drafts.get(0).date()).isEqualTo(LocalDate.of(2026, 11, 5));
        assertThat(drafts.get(11).date()).isEqualTo(LocalDate.of(2027, 1, 21));
        assertThat(drafts).allSatisfy(draft -> {
            assertThat(draft.date().getDayOfWeek()).isEqualTo(DayOfWeek.THURSDAY);
            assertThat(draft.startTime()).isEqualTo(LocalTime.of(14, 0));
        });
    }

    @Test
    void 주_2회면_요일_순서대로_번갈아_나온다() {
        List<SessionDraft> drafts = SessionScheduleExpander.expand(new RepeatRule(
            LocalDate.of(2026, 11, 2),      // 월요일
            Set.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY),
            LocalTime.of(10, 0), LocalTime.of(12, 0),
            5));

        assertThat(drafts).extracting(SessionDraft::date).containsExactly(
            LocalDate.of(2026, 11, 2),   // 월
            LocalDate.of(2026, 11, 4),   // 수
            LocalDate.of(2026, 11, 9),   // 월
            LocalDate.of(2026, 11, 11),  // 수
            LocalDate.of(2026, 11, 16)); // 월
    }

    @Test
    void 첫_수업일이_반복요일이_아니면_다음_해당요일부터_시작한다() {
        List<SessionDraft> drafts = SessionScheduleExpander.expand(new RepeatRule(
            LocalDate.of(2026, 11, 3),      // 화요일
            Set.of(DayOfWeek.THURSDAY),
            LocalTime.of(14, 0), LocalTime.of(16, 0),
            2));

        assertThat(drafts).extracting(SessionDraft::date).containsExactly(
            LocalDate.of(2026, 11, 5), LocalDate.of(2026, 11, 12));
    }

    @Test
    void 종료시각이_시작시각보다_빠르면_거부한다() {
        assertThatThrownBy(() -> SessionScheduleExpander.expand(new RepeatRule(
            LocalDate.of(2026, 11, 5), Set.of(DayOfWeek.THURSDAY),
            LocalTime.of(16, 0), LocalTime.of(14, 0), 3)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("endTime");
    }

    @Test
    void 회차수가_0이하면_거부한다() {
        assertThatThrownBy(() -> SessionScheduleExpander.expand(new RepeatRule(
            LocalDate.of(2026, 11, 5), Set.of(DayOfWeek.THURSDAY),
            LocalTime.of(14, 0), LocalTime.of(16, 0), 0)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("count");
    }

    @Test
    void 회차수_상한은_200이다() {
        assertThatThrownBy(() -> SessionScheduleExpander.expand(new RepeatRule(
            LocalDate.of(2026, 11, 5), Set.of(DayOfWeek.THURSDAY),
            LocalTime.of(14, 0), LocalTime.of(16, 0), 201)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("count");
    }

    @Test
    void 반복요일이_비면_거부한다() {
        assertThatThrownBy(() -> SessionScheduleExpander.expand(new RepeatRule(
            LocalDate.of(2026, 11, 5), Set.of(),
            LocalTime.of(14, 0), LocalTime.of(16, 0), 3)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("daysOfWeek");
    }
}
