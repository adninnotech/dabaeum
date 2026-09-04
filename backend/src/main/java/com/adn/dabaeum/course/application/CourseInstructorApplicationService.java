package com.adn.dabaeum.course.application;

import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import java.util.List;
import java.util.UUID;

public interface CourseInstructorApplicationService {

    List<CourseInstructorView> list(UUID courseId);

    CourseInstructorView assign(
        AssignCourseInstructorCommand command,
        AuthenticatedUserContext context
    );

    CourseInstructorView updateRole(
        UpdateCourseInstructorCommand command,
        AuthenticatedUserContext context
    );

    CourseInstructorView remove(
        UUID courseId,
        UUID userId,
        AuthenticatedUserContext context
    );
}
