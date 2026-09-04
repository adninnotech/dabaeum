package com.adn.dabaeum.course.api;

import com.adn.dabaeum.course.domain.CourseInstructorRole;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

public record InstructorCourseResponse(
    @JsonUnwrapped CourseResponse course,
    CourseInstructorRole instructorRole,
    long enrolledCount
) {
}
