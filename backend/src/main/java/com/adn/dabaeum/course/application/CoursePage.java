package com.adn.dabaeum.course.application;

import com.adn.dabaeum.course.domain.CourseView;
import java.util.List;

public record CoursePage(
    List<CourseView> data,
    int page,
    int size,
    long totalElements,
    int totalPages
) {
}
