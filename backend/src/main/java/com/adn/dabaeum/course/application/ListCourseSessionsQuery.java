package com.adn.dabaeum.course.application;

import java.util.UUID;

public record ListCourseSessionsQuery(
    UUID courseId,
    int page,
    int size,
    String sort
) {
}
