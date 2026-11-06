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
    Instant updatedAt,
    String topic
) {

    private static final int MAX_TOPIC_LENGTH = 200;

    public CourseSession {

        if (id == null || courseId == null
            || status == null || createdAt == null || updatedAt == null) {
            throw new IllegalArgumentException("required course session field is missing");
        }
        if (sessionNo < 1) {
            throw new IllegalArgumentException("sessionNo must be at least 1");
        }
        if ((startsAt == null) != (endsAt == null)) {
            throw new IllegalArgumentException(
                "startsAt and endsAt must be both present or both absent");
        }
        if (startsAt != null && endsAt != null && !startsAt.isBefore(endsAt)) {
            throw new IllegalArgumentException("startsAt must be before endsAt");
        }
        if (attendanceOpensAt != null && attendanceClosesAt != null
            && !attendanceOpensAt.isBefore(attendanceClosesAt)) {
            throw new IllegalArgumentException(
                "attendanceOpensAt must be before attendanceClosesAt"
            );
        }
        if (topic != null && topic.length() > MAX_TOPIC_LENGTH) {
            throw new IllegalArgumentException(
                "topic must not exceed " + MAX_TOPIC_LENGTH + " characters");
        }
    }

    public Instant attendanceOpensAtOrStart() {
        return attendanceOpensAt != null ? attendanceOpensAt : startsAt;
    }

    public Instant attendanceClosesAtOrEnd() {
        return attendanceClosesAt != null ? attendanceClosesAt : endsAt;
    }

}
