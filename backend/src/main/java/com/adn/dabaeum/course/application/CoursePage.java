package com.adn.dabaeum.course.application;

import com.adn.dabaeum.course.domain.Course;
import java.util.List;

public record CoursePage(
    List<Course> data,
    int page,
    int size,
    long totalElements,
    int totalPages
) {
}
