package com.adn.dabaeum.institution.application;

import com.adn.dabaeum.institution.domain.InstitutionJoinApplication;
import java.util.List;

public record InstitutionJoinApplicationPage(
    List<InstitutionJoinApplication> data,
    int page,
    int size,
    long totalElements,
    int totalPages
) {
}
