package com.adn.dabaeum.institution.application;

import com.adn.dabaeum.institution.domain.Institution;
import java.util.List;

public record InstitutionPage(
    List<Institution> data,
    int page,
    int size,
    long totalElements,
    int totalPages
) {
}
