package com.adn.dabaeum.course.domain;

public record InstructorCourseStats(
    long total,
    long recruiting,
    long inProgress,
    long completed
) {
}
