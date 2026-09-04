package com.adn.dabaeum.course.domain;

import java.util.List;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface CourseSessionRepository {

    void save(CourseSession session);

    Optional<CourseSession> findById(UUID sessionId);

    Optional<CourseSession> findByIdForUpdate(UUID sessionId);

    List<CourseSession> findAttendanceEligibleByCourseId(
        UUID courseId,
        Instant evaluatedAt
    );

    boolean existsAttendanceIneligibleByCourseId(UUID courseId, Instant evaluatedAt);

    Optional<CourseSession> findByCourseIdAndSessionNo(UUID courseId, int sessionNo);

    List<CourseSession> findPageByCourseId(CourseSessionPageCriteria criteria);

    long countByCourseId(UUID courseId);

    boolean update(CourseSession session);
}
