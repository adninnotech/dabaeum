package com.adn.dabaeum.attendance.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AttendanceRepository {

    void save(Attendance attendance);

    Optional<Attendance> findById(UUID id);

    Optional<Attendance> findByIdForUpdate(UUID id);

    Optional<Attendance> findBySessionAndEnrollment(UUID sessionId, UUID enrollmentId);

    List<Attendance> findBySession(UUID sessionId, AttendancePageCriteria criteria);

    long countBySession(UUID sessionId);

    List<Attendance> findByEnrollment(UUID enrollmentId);

    boolean updateStatus(
        UUID id,
        AttendanceStatus expected,
        AttendanceStatus next,
        Instant updatedAt
    );
}
