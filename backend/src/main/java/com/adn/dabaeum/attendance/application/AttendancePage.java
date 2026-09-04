package com.adn.dabaeum.attendance.application;

import com.adn.dabaeum.attendance.domain.Attendance;
import java.util.List;

public record AttendancePage(
    List<Attendance> data,
    int page,
    int size,
    long totalElements,
    int totalPages
) {
    public AttendancePage {
        data = List.copyOf(data);
    }
}
