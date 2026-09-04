package com.adn.dabaeum.institution.application;

import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import com.adn.dabaeum.institution.domain.InstitutionJoinApplication;
import com.adn.dabaeum.institution.domain.InstitutionJoinApplicationStatus;
import java.util.UUID;

public interface InstitutionJoinApplicationService {

    InstitutionJoinApplication apply(ApplyInstitutionJoinCommand command);

    InstitutionJoinApplication get(UUID applicationId, AuthenticatedUserContext actor);

    InstitutionJoinApplicationPage list(
        AuthenticatedUserContext actor, InstitutionJoinApplicationStatus status,
        int page, int size);

    InstitutionJoinApplication approve(
        UUID applicationId, AuthenticatedUserContext actor);

    InstitutionJoinApplication reject(
        UUID applicationId, String rejectionReason, AuthenticatedUserContext actor);
}
