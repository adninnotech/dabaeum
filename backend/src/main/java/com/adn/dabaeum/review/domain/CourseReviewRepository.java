package com.adn.dabaeum.review.domain;

import java.util.List;
import java.util.UUID;

public interface CourseReviewRepository {

    void save(CourseReview review);

    boolean existsByUserIdAndCourseId(UUID userId, UUID courseId);

    boolean hasConfirmedCompletion(UUID userId, UUID courseId);

    List<CourseReviewView> findByUserId(UUID userId, int limit, int offset);

    long countByUserId(UUID userId);

    List<CourseReviewView> findByCourseId(UUID courseId, int limit, int offset);

    long countByCourseId(UUID courseId);
}
