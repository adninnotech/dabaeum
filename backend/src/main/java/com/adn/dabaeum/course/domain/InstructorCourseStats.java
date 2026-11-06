package com.adn.dabaeum.course.domain;

public record InstructorCourseStats(
    long total,
    long recruiting,
    long inProgress,
    long completed,
    long studentCount,
    long inquiryCount
) {
}
