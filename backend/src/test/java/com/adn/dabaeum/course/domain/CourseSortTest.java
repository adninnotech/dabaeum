package com.adn.dabaeum.course.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class CourseSortTest {

    @ParameterizedTest
    @MethodSource("sortValues")
    void mapsOnlyWhitelistedSortFields(String value, CourseSort expected) {
        assertThat(CourseSort.fromApiValue(value)).isEqualTo(expected);
    }

    @Test
    void rejectsUnknownSortField() {
        assertThatThrownBy(() -> CourseSort.fromApiValue("institutionId"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Unsupported course sort");
    }

    @Test
    void mapsOnlyWhitelistedDirections() {
        assertThat(CourseSortDirection.fromApiValue("asc"))
            .isEqualTo(CourseSortDirection.ASC);
        assertThat(CourseSortDirection.fromApiValue("desc"))
            .isEqualTo(CourseSortDirection.DESC);
        assertThatThrownBy(() -> CourseSortDirection.fromApiValue("sideways"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void enforcesPageBounds() {
        assertThatThrownBy(() -> new CoursePageCriteria(
            -1, 20, CourseSort.CREATED_AT, CourseSortDirection.DESC))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CoursePageCriteria(
            0, 0, CourseSort.CREATED_AT, CourseSortDirection.DESC))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CoursePageCriteria(
            0, 101, CourseSort.CREATED_AT, CourseSortDirection.DESC))
            .isInstanceOf(IllegalArgumentException.class);
    }

    private static Stream<Arguments> sortValues() {
        return Stream.of(
            Arguments.of("createdAt", CourseSort.CREATED_AT),
            Arguments.of("updatedAt", CourseSort.UPDATED_AT),
            Arguments.of("courseCode", CourseSort.COURSE_CODE),
            Arguments.of("title", CourseSort.TITLE),
            Arguments.of("status", CourseSort.STATUS),
            Arguments.of("startDate", CourseSort.START_DATE),
            Arguments.of("endDate", CourseSort.END_DATE)
        );
    }
}
