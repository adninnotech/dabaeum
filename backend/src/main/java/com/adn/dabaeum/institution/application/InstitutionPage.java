package com.adn.dabaeum.institution.application;

import com.adn.dabaeum.institution.domain.InstitutionListItem;
import java.util.List;

public record InstitutionPage(
    List<InstitutionListItem> data,
    int page,
    int size,
    long totalElements,
    int totalPages
) {
}
