package com.adn.dabaeum.course.domain;

import java.util.UUID;

public record CourseSessionPageCriteria(
    UUID courseId,
    int offset,
    int limit,
    CourseSessionSort sort,
    CourseSessionSortDirection direction
) {
}
