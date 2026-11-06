package com.adn.dabaeum.institution.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InstitutionJoinApplicationRepository {

    void save(InstitutionJoinApplication application);

    Optional<InstitutionJoinApplication> findById(UUID id);

    Optional<InstitutionJoinApplication> findByIdForUpdate(UUID id);

    /** applicantUserId 가 null 이면 모든 신청자를 본다. */
    List<InstitutionJoinApplication> findPage(
        InstitutionJoinApplicationStatus status, UUID applicantUserId, int limit, int offset);

    long count(InstitutionJoinApplicationStatus status, UUID applicantUserId);

    boolean existsPendingByApplicant(UUID applicantUserId);

    boolean updateDecision(
        InstitutionJoinApplication application,
        InstitutionJoinApplicationStatus expectedStatus);
}
