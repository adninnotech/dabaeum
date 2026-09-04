package com.adn.dabaeum.institution.application;

import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import java.time.Instant;

public record ApplyInstitutionJoinCommand(
    String institutionName,
    String institutionCode,
    String representativeName,
    String contactEmail,
    String contactPhone,
    String address,
    AuthenticatedUserContext actor,
    Instant requestedAt
) {
}
