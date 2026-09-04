package com.adn.dabaeum.course.application;

import com.adn.dabaeum.course.domain.CourseSessionStatus;
import java.time.Instant;
import java.util.UUID;

public record UpdateCourseSessionCommand(
    UUID sessionId,
    CourseSessionUpdateField<Integer> sessionNo,
    CourseSessionUpdateField<Instant> startsAt,
    CourseSessionUpdateField<Instant> endsAt,
    CourseSessionUpdateField<String> location,
    CourseSessionUpdateField<Instant> attendanceOpensAt,
    CourseSessionUpdateField<Instant> attendanceClosesAt,
    CourseSessionUpdateField<CourseSessionStatus> status
) {

    public int presentFieldCount() {
        return count(sessionNo) + count(startsAt) + count(endsAt) + count(location)
            + count(attendanceOpensAt) + count(attendanceClosesAt) + count(status);
    }

    private int count(CourseSessionUpdateField<?> field) {
        return field != null && field.present() ? 1 : 0;
    }
}
