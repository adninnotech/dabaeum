package com.adn.dabaeum.course.domain;

import java.util.List;
import java.util.UUID;

public interface CourseQueryRepository {

    List<InstructorCourseView> findInstructorCourses(
        UUID instructorUserId, CourseStatus status, int limit, int offset, String sort);

    long countInstructorCourses(UUID instructorUserId, CourseStatus status);

    InstructorCourseStats instructorCourseStats(UUID instructorUserId);

    List<Course> findInstitutionCourses(
        UUID institutionId, CourseStatus status, int limit, int offset, String sort);

    long countInstitutionCourses(UUID institutionId, CourseStatus status);

    List<InstitutionInstructorView> findInstitutionInstructors(
        UUID institutionId, String status, int limit, int offset, String sort);

    long countInstitutionInstructors(UUID institutionId, String status);
}
