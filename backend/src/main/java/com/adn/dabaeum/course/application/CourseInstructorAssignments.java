package com.adn.dabaeum.course.application;

import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import com.adn.dabaeum.course.domain.Course;
import com.adn.dabaeum.course.domain.CourseInstructorRepository;
import com.adn.dabaeum.course.domain.CourseInstructorRole;
import java.util.Objects;

/**
 * 요청자가 그 강좌 기관의 강사이면서 강좌에 배정됐는지 본다. mainOnly 면 주강사 배정만 인정한다.
 */
public final class CourseInstructorAssignments {

    private CourseInstructorAssignments() {
    }

    public static boolean isAssigned(
        CourseInstructorRepository instructorRepository,
        AuthenticatedUserContext context,
        Course course,
        boolean mainOnly
    ) {
        if (context == null || context.roles().stream().noneMatch(role ->
            "INSTRUCTOR".equals(role.role())
                && Objects.equals(role.institutionId(), course.institutionId()))) {
            return false;
        }
        return mainOnly
            ? instructorRepository.existsByCourseIdAndUserIdAndRole(
                course.id(), context.userId(), CourseInstructorRole.MAIN)
            : instructorRepository.existsByCourseIdAndUserId(course.id(), context.userId());
    }
}
