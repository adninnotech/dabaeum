package com.adn.dabaeum.interest.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CourseInterestRepository {

    void save(CourseInterest interest);

    Optional<CourseInterest> findById(UUID id);

    Optional<CourseInterest> findByUserIdAndCourseId(UUID userId, UUID courseId);

    List<CourseInterestView> findByUserId(UUID userId, int limit, int offset);

    long countByUserId(UUID userId);

    boolean delete(UUID id);
}
