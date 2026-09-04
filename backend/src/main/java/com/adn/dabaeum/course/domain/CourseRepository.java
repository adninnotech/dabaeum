package com.adn.dabaeum.course.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CourseRepository {

    void save(Course course);

    Optional<Course> findById(UUID id);

    Optional<Course> findActiveById(UUID id);

    Optional<Course> findActiveByIdForUpdate(UUID id);

    Optional<Course> findActiveByInstitutionAndCode(
        UUID institutionId,
        String courseCode
    );

    List<Course> findActivePage(CoursePageCriteria criteria);

    long countActive();

    boolean updateActive(Course course);
}
