package com.adn.dabaeum.usage.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** docs/specs/usage-statistics.md §3.2 — 기록 순간의 구간값만 남긴다. 연령 규칙은 수강 통계와 같다. */
class VisitorSegmentTest {

    private static final LocalDate ON = LocalDate.of(2026, 10, 30);

    @Test
    void guestHasNoGenderOrAge() {
        assertThat(VisitorSegment.GUEST).isEqualTo(new VisitorSegment("GUEST", "UNKNOWN", "UNKNOWN"));
    }

    @Test
    void memberAgeBandIsFullAgeOnRecordDayInTens() {
        assertThat(VisitorSegment.member("FEMALE", LocalDate.of(1996, 10, 30), ON).ageBand()).isEqualTo("30");
        assertThat(VisitorSegment.member("FEMALE", LocalDate.of(1996, 10, 31), ON).ageBand()).isEqualTo("20");
    }

    @Test
    void ageBandIsClampedToTeensAndSeventies() {
        assertThat(VisitorSegment.member(null, LocalDate.of(2020, 1, 1), ON).ageBand()).isEqualTo("10");
        assertThat(VisitorSegment.member(null, LocalDate.of(1930, 1, 1), ON).ageBand()).isEqualTo("70");
        assertThat(VisitorSegment.member(null, null, ON).ageBand()).isEqualTo("UNKNOWN");
    }

    @Test
    void unknownOrMissingGenderBecomesUnknown() {
        assertThat(VisitorSegment.member("MALE", null, ON).gender()).isEqualTo("MALE");
        assertThat(VisitorSegment.member("UNDISCLOSED", null, ON).gender()).isEqualTo("UNDISCLOSED");
        assertThat(VisitorSegment.member(null, null, ON).gender()).isEqualTo("UNKNOWN");
        assertThat(VisitorSegment.member("OTHER", null, ON).gender()).isEqualTo("UNKNOWN");
        assertThat(VisitorSegment.member(null, null, ON).memberType()).isEqualTo("MEMBER");
    }
}
