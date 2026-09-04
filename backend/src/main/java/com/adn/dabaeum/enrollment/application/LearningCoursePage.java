package com.adn.dabaeum.enrollment.application;

import com.adn.dabaeum.enrollment.domain.LearningCourseView;
import java.util.List;

public record LearningCoursePage(
    List<LearningCourseView> data,
    int page,
    int size,
    long totalElements,
    int totalPages
) {
}
