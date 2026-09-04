package com.adn.dabaeum.enrollment.application;

import com.adn.dabaeum.enrollment.domain.EnrollmentProgressView;
import java.util.List;

public record EnrollmentProgressPage(
    List<EnrollmentProgressView> data,
    int page,
    int size,
    long totalElements,
    int totalPages
) {
}
