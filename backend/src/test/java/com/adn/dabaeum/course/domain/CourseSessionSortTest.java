package com.adn.dabaeum.course.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class CourseSessionSortTest {

    @Test
    void acceptsOnlyDocumentedSortValues() {
        assertThat(CourseSessionSort.fromApiValue("sessionNo"))
            .isEqualTo(CourseSessionSort.SESSION_NO);
        assertThat(CourseSessionSort.fromApiValue("startsAt"))
            .isEqualTo(CourseSessionSort.STARTS_AT);
        assertThat(CourseSessionSort.fromApiValue("endsAt"))
            .isEqualTo(CourseSessionSort.ENDS_AT);
        assertThat(CourseSessionSort.fromApiValue("status"))
            .isEqualTo(CourseSessionSort.STATUS);
        assertThat(CourseSessionSort.fromApiValue("createdAt"))
            .isEqualTo(CourseSessionSort.CREATED_AT);
        assertThat(CourseSessionSort.fromApiValue("updatedAt"))
            .isEqualTo(CourseSessionSort.UPDATED_AT);
        assertThat(CourseSessionSortDirection.fromApiValue("asc"))
            .isEqualTo(CourseSessionSortDirection.ASC);
        assertThat(CourseSessionSortDirection.fromApiValue("desc"))
            .isEqualTo(CourseSessionSortDirection.DESC);
    }

    @Test
    void rejectsUnknownSortAndDirection() {
        assertThatThrownBy(() -> CourseSessionSort.fromApiValue("courseId"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CourseSessionSortDirection.fromApiValue("sideways"))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
