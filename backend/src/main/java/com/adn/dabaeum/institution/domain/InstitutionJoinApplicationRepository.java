package com.adn.dabaeum.institution.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InstitutionJoinApplicationRepository {

    void save(InstitutionJoinApplication application);

    Optional<InstitutionJoinApplication> findById(UUID id);

    Optional<InstitutionJoinApplication> findByIdForUpdate(UUID id);

    List<InstitutionJoinApplication> findPage(
        InstitutionJoinApplicationStatus status, int limit, int offset);

    long count(InstitutionJoinApplicationStatus status);

    boolean updateDecision(
        InstitutionJoinApplication application,
        InstitutionJoinApplicationStatus expectedStatus);
}
