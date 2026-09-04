package com.adn.dabaeum.enrollment.application;

import com.adn.dabaeum.enrollment.domain.InstitutionEnrollmentView;
import java.util.List;

public record InstitutionEnrollmentPage(
    List<InstitutionEnrollmentView> data,
    int page,
    int size,
    long totalElements,
    int totalPages
) {
}
