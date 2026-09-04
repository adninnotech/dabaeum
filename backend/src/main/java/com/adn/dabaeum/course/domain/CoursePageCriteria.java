package com.adn.dabaeum.course.domain;

import java.util.Objects;

public record CoursePageCriteria(
    int offset,
    int limit,
    CourseSort sort,
    CourseSortDirection direction
) {

    public CoursePageCriteria {
        if (offset < 0) {
            throw new IllegalArgumentException("offset must be non-negative");
        }
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("limit must be between 1 and 100");
        }
        Objects.requireNonNull(sort, "sort");
        Objects.requireNonNull(direction, "direction");
    }
}
