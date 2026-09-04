package com.adn.dabaeum.course.application;

import com.adn.dabaeum.course.domain.InstructorCourseView;
import java.util.List;

public record InstructorCoursePage(
    List<InstructorCourseView> data,
    int page,
    int size,
    long totalElements,
    int totalPages
) {
}
