package com.adn.dabaeum.course.api;

public record InstructorCourseStatsResponse(
    long total,
    long recruiting,
    long inProgress,
    long completed
) {
}
