package com.adn.dabaeum.interest.application;

import com.adn.dabaeum.interest.domain.CourseInterestView;
import java.util.List;

public record CourseInterestPage(
    List<CourseInterestView> data,
    int page,
    int size,
    long totalElements,
    int totalPages
) {
}
