package com.adn.dabaeum.course.application;

import com.adn.dabaeum.course.domain.CourseInstructorRole;
import java.util.UUID;

public record AssignCourseInstructorCommand(
    UUID courseId,
    UUID userId,
    CourseInstructorRole role
) {
}
