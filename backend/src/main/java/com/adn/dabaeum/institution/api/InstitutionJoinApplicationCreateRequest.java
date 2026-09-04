package com.adn.dabaeum.institution.api;

public record InstitutionJoinApplicationCreateRequest(
    String institutionName,
    String institutionCode,
    String representativeName,
    String contactEmail,
    String contactPhone,
    String address
) {
}
