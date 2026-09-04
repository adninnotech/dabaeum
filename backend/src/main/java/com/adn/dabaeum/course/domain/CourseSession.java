package com.adn.dabaeum.course.domain;

import java.time.Instant;
import java.util.UUID;

public record CourseSession(
    UUID id,
    UUID courseId,
    int sessionNo,
    Instant startsAt,
    Instant endsAt,
    String location,
    Instant attendanceOpensAt,
    Instant attendanceClosesAt,
    CourseSessionStatus status,
    Instant createdAt,
    Instant updatedAt
) {

    public CourseSession {
        if (id == null || courseId == null || startsAt == null || endsAt == null
            || status == null || createdAt == null || updatedAt == null) {
            throw new IllegalArgumentException("required course session field is missing");
        }
        if (sessionNo < 1) {
            throw new IllegalArgumentException("sessionNo must be at least 1");
        }
        if (!startsAt.isBefore(endsAt)) {
            throw new IllegalArgumentException("startsAt must be before endsAt");
        }
        if (attendanceOpensAt != null && attendanceClosesAt != null
            && !attendanceOpensAt.isBefore(attendanceClosesAt)) {
            throw new IllegalArgumentException(
                "attendanceOpensAt must be before attendanceClosesAt"
            );
        }
    }
}
