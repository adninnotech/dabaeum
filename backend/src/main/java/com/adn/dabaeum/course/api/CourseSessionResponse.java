package com.adn.dabaeum.course.api;

import com.adn.dabaeum.course.domain.CourseSessionStatus;
import java.time.Instant;
import java.util.UUID;

public record CourseSessionResponse(
    UUID id, UUID courseId, int sessionNo, Instant startsAt, Instant endsAt,
    String location, Instant attendanceOpensAt, Instant attendanceClosesAt,
    CourseSessionStatus status, Instant createdAt, Instant updatedAt
) {
}
