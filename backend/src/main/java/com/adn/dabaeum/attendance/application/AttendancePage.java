package com.adn.dabaeum.attendance.application;

import com.adn.dabaeum.attendance.domain.Attendance;
import com.adn.dabaeum.attendance.domain.AttendanceLearner;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** {@code learners} 는 수강신청 id 로 찾는 이 페이지분 수강생이다. 없는 항목은 이름 없이 나간다. */
public record AttendancePage(
    List<Attendance> data,
    int page,
    int size,
    long totalElements,
    int totalPages,
    Map<UUID, AttendanceLearner> learners
) {
    public AttendancePage {
        data = List.copyOf(data);
        learners = Map.copyOf(learners);
    }

    public AttendancePage(
        List<Attendance> data, int page, int size, long totalElements, int totalPages
    ) {
        this(data, page, size, totalElements, totalPages, Map.of());
    }
}
