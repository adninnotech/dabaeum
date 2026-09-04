package com.adn.dabaeum.enrollment.application;

import java.util.UUID;

public record ListCourseEnrollmentsQuery(
    UUID courseId,
    int page,
    int size,
    String sort
) {
}
