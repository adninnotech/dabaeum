package com.adn.dabaeum.institution.application;

import com.adn.dabaeum.institution.domain.InstitutionStatus;

public record CreateInstitutionCommand(
    String institutionCode,
    String name,
    String businessNumber,
    String representativeName,
    String address,
    String contactPhone,
    String contactEmail,
    InstitutionStatus status
) {
}
