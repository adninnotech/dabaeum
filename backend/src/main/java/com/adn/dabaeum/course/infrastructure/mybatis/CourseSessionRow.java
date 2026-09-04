package com.adn.dabaeum.course.infrastructure.mybatis;

import java.time.Instant;
import java.util.UUID;

public record CourseSessionRow(
    UUID id,
    UUID courseId,
    Integer sessionNo,
    Instant startsAt,
    Instant endsAt,
    String location,
    Instant attendanceOpensAt,
    Instant attendanceClosesAt,
    String status,
    Instant createdAt,
    Instant updatedAt
) {
}
