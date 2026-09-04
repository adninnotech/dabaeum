package com.adn.dabaeum.enrollment.application;

import com.adn.dabaeum.enrollment.domain.MyEnrollmentView;
import java.util.List;

public record MyEnrollmentPage(
    List<MyEnrollmentView> data,
    int page,
    int size,
    long totalElements,
    int totalPages
) {
}
