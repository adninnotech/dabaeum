package com.adn.dabaeum.instructor.domain;

import java.util.UUID;

public record InstructorApplicationPageCriteria(
    UUID userId,
    UUID institutionId,
    InstructorApplicationStatus status,
    int offset,
    int limit,
    String sort,
    String direction
) {

    public InstructorApplicationPageCriteria {
        if (offset < 0 || limit < 1) {
            throw new IllegalArgumentException("invalid page criteria");
        }
        if (!java.util.Set.of("APPLIED_AT", "REVIEWED_AT", "STATUS").contains(sort)) {
            throw new IllegalArgumentException("invalid sort");
        }
        if (!java.util.Set.of("ASC", "DESC").contains(direction)) {
            throw new IllegalArgumentException("invalid direction");
        }
    }
}
