package com.adn.dabaeum.course.application;

import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import com.adn.dabaeum.course.domain.CourseStatus;
import com.adn.dabaeum.course.domain.InstructorCourseStats;
import java.util.UUID;

public interface CourseQueryService {

    InstructorCoursePage listInstructorCourses(
        AuthenticatedUserContext actor, CourseStatus status,
        int page, int size, String sort);

    InstructorCourseStats instructorCourseStats(AuthenticatedUserContext actor);

    CoursePage listInstitutionCourses(
        AuthenticatedUserContext actor, UUID institutionId, CourseStatus status,
        int page, int size, String sort);

    InstitutionInstructorPage listInstitutionInstructors(
        AuthenticatedUserContext actor, UUID institutionId, String status,
        int page, int size, String sort);
}
