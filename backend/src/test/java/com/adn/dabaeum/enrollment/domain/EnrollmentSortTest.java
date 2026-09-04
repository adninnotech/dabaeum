package com.adn.dabaeum.enrollment.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class EnrollmentSortTest {

    @Test
    void mapsSupportedApiSortValues() {
        assertThat(EnrollmentSort.fromApiValue("createdAt")).isEqualTo(EnrollmentSort.CREATED_AT);
        assertThat(EnrollmentSort.fromApiValue("updatedAt")).isEqualTo(EnrollmentSort.UPDATED_AT);
        assertThat(EnrollmentSort.fromApiValue("appliedAt")).isEqualTo(EnrollmentSort.APPLIED_AT);
        assertThat(EnrollmentSort.fromApiValue("status")).isEqualTo(EnrollmentSort.STATUS);
        assertThat(EnrollmentSort.fromApiValue("userId")).isEqualTo(EnrollmentSort.USER_ID);
        assertThat(EnrollmentSortDirection.fromApiValue("asc"))
            .isEqualTo(EnrollmentSortDirection.ASC);
        assertThat(EnrollmentSortDirection.fromApiValue("desc"))
            .isEqualTo(EnrollmentSortDirection.DESC);
    }

    @Test
    void rejectsUnsupportedValuesAndInvalidPageBounds() {
        assertThatThrownBy(() -> EnrollmentSort.fromApiValue("courseId"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> EnrollmentSortDirection.fromApiValue("sideways"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EnrollmentPageCriteria(
            java.util.UUID.randomUUID(), -1, 10, EnrollmentSort.CREATED_AT,
            EnrollmentSortDirection.ASC)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EnrollmentPageCriteria(
            java.util.UUID.randomUUID(), 0, 101, EnrollmentSort.CREATED_AT,
            EnrollmentSortDirection.ASC)).isInstanceOf(IllegalArgumentException.class);
    }
}
