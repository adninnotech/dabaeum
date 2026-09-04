package com.adn.dabaeum.course.application;

import com.adn.dabaeum.course.domain.InstitutionInstructorView;
import java.util.List;

public record InstitutionInstructorPage(
    List<InstitutionInstructorView> data,
    int page,
    int size,
    long totalElements,
    int totalPages
) {
}
