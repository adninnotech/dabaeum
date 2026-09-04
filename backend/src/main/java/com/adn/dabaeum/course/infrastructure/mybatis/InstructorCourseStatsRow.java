package com.adn.dabaeum.course.infrastructure.mybatis;

public record InstructorCourseStatsRow(
    Long total,
    Long recruiting,
    Long inProgress,
    Long completed
) {
}
