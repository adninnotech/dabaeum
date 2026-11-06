package com.adn.dabaeum.institution.application;

import com.adn.dabaeum.institution.domain.InstitutionStatus;

public record ListInstitutionsQuery(
    int page, int size, String sort, String keyword, InstitutionStatus status, String region
) {
}
