package com.adn.dabaeum.attendance.domain;

import java.util.Objects;
import java.util.UUID;

public record AttendancePageCriteria(
    UUID sessionId,
    int offset,
    int limit,
    AttendanceSort sort,
    AttendanceSortDirection direction
) {

    public AttendancePageCriteria {
        Objects.requireNonNull(sessionId, "sessionId");
        if (offset < 0) {
            throw new IllegalArgumentException("offset must be non-negative");
        }
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("limit must be between 1 and 100");
        }
        Objects.requireNonNull(sort, "sort");
        Objects.requireNonNull(direction, "direction");
    }

    public AttendancePageCriteria(UUID sessionId, int offset, int limit) {
        this(sessionId, offset, limit, AttendanceSort.CHECKED_AT, AttendanceSortDirection.ASC);
    }
}
