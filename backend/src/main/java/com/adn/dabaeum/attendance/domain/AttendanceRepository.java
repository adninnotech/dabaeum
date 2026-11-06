package com.adn.dabaeum.attendance.domain;

import java.time.Instant;
import java.util.Collection;
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

    /** 출결 목록 한 페이지분 수강생 이름을 한 번에 읽는다. 없는 id 는 결과에서 빠진다. */
    List<AttendanceLearner> findLearners(Collection<UUID> enrollmentIds);

    List<Attendance> findByEnrollment(UUID enrollmentId);

    boolean updateStatus(
        UUID id,
        AttendanceStatus expected,
        AttendanceStatus next,
        Instant updatedAt
    );
}
