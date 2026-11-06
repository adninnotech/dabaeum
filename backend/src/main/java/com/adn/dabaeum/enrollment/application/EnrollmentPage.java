package com.adn.dabaeum.enrollment.application;

import com.adn.dabaeum.enrollment.domain.EnrollmentListItem;
import java.util.List;

public record EnrollmentPage(
    List<EnrollmentListItem> data,
    int page,
    int size,
    long totalElements,
    int totalPages
) {
    public EnrollmentPage {
        data = List.copyOf(data);
    }
}
