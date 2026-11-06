package com.adn.dabaeum.course.domain;

import java.util.Objects;

public record InstructorCourseView(
    Course course,
    CourseInstructorRole instructorRole,
    long enrolledCount,
    String institutionName,
    String mainInstructorName
) {

    public InstructorCourseView {
        Objects.requireNonNull(course, "course");
        Objects.requireNonNull(instructorRole, "instructorRole");
    }
}
