package com.adn.dabaeum.course.application;

import com.adn.dabaeum.course.domain.CourseSessionStatus;
import java.time.Instant;
import java.util.UUID;

public record CreateCourseSessionCommand(
    UUID courseId,
    Integer sessionNo,
    Instant startsAt,
    Instant endsAt,
    String location,
    Instant attendanceOpensAt,
    Instant attendanceClosesAt,
    CourseSessionStatus status
) {
}
