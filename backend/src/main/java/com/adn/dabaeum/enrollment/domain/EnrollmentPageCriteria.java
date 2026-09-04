package com.adn.dabaeum.enrollment.domain;

import java.util.Objects;
import java.util.UUID;

public record EnrollmentPageCriteria(
    UUID courseId,
    int offset,
    int limit,
    EnrollmentSort sort,
    EnrollmentSortDirection direction
) {

    public EnrollmentPageCriteria {
        Objects.requireNonNull(courseId, "courseId");
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
