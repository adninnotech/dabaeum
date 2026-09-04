package com.adn.dabaeum.attendance.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class AttendanceSortTest {

    @Test
    void mapsOnlyAllowlistedSortFieldsAndDirections() {
        assertThat(AttendanceSort.fromApiValue("checkedAt"))
            .isEqualTo(AttendanceSort.CHECKED_AT);
        assertThat(AttendanceSort.fromApiValue("createdAt"))
            .isEqualTo(AttendanceSort.CREATED_AT);
        assertThat(AttendanceSort.fromApiValue("status"))
            .isEqualTo(AttendanceSort.STATUS);
        assertThat(AttendanceSortDirection.fromApiValue("asc"))
            .isEqualTo(AttendanceSortDirection.ASC);
        assertThat(AttendanceSortDirection.fromApiValue("desc"))
            .isEqualTo(AttendanceSortDirection.DESC);
    }

    @Test
    void rejectsUnknownSortValuesAndInvalidPageBounds() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> AttendanceSort.fromApiValue("enrollmentId"));
        assertThatIllegalArgumentException()
            .isThrownBy(() -> AttendanceSortDirection.fromApiValue("sideways"));
        assertThatIllegalArgumentException()
            .isThrownBy(() -> new AttendancePageCriteria(
                UUID.randomUUID(), -1, 20,
                AttendanceSort.CHECKED_AT, AttendanceSortDirection.ASC));
        assertThatIllegalArgumentException()
            .isThrownBy(() -> new AttendancePageCriteria(
                UUID.randomUUID(), 0, 0,
                AttendanceSort.CHECKED_AT, AttendanceSortDirection.ASC));
        assertThatIllegalArgumentException()
            .isThrownBy(() -> new AttendancePageCriteria(
                UUID.randomUUID(), 0, 101,
                AttendanceSort.CHECKED_AT, AttendanceSortDirection.ASC));
    }

    @Test
    void defaultsToCheckedAtAscending() {
        AttendancePageCriteria criteria = new AttendancePageCriteria(UUID.randomUUID(), 0, 20);

        assertThat(criteria.sort()).isEqualTo(AttendanceSort.CHECKED_AT);
        assertThat(criteria.direction()).isEqualTo(AttendanceSortDirection.ASC);
    }
}
