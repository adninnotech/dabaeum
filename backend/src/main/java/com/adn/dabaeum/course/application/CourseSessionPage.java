package com.adn.dabaeum.course.application;

import com.adn.dabaeum.course.domain.CourseSession;
import java.util.List;

public record CourseSessionPage(
    List<CourseSession> data,
    int page,
    int size,
    long totalElements,
    int totalPages
) {
}
