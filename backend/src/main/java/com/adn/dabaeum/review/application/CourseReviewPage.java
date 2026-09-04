package com.adn.dabaeum.review.application;

import com.adn.dabaeum.review.domain.CourseReviewView;
import java.util.List;

public record CourseReviewPage(
    List<CourseReviewView> data,
    int page,
    int size,
    long totalElements,
    int totalPages
) {
}
