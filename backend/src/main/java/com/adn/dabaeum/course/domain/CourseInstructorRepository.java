package com.adn.dabaeum.course.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CourseInstructorRepository {

    void save(CourseInstructor instructor);

    Optional<CourseInstructor> findByCourseIdAndUserId(
        UUID courseId,
        UUID userId
    );

    List<CourseInstructor> findByCourseId(UUID courseId);

    boolean existsByCourseIdAndUserId(UUID courseId, UUID userId);

    boolean existsByCourseIdAndUserIdAndRole(
        UUID courseId,
        UUID userId,
        CourseInstructorRole role
    );

    boolean existsByUserIdAndInstitutionId(UUID userId, UUID institutionId);

    boolean updateRole(
        CourseInstructor instructor,
        CourseInstructorRole expectedRole
    );

    boolean deleteByCourseIdAndUserId(UUID courseId, UUID userId);
}
