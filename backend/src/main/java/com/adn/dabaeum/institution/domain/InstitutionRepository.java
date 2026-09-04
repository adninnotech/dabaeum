package com.adn.dabaeum.institution.domain;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

public interface InstitutionRepository {

    void save(Institution institution);

    Optional<Institution> findById(UUID id);

    Optional<Institution> findActiveById(UUID id);

    Optional<Institution> findActiveByCode(String institutionCode);

    List<Institution> findActivePage(InstitutionPageCriteria criteria);

    long countActive();

    boolean updateActive(Institution institution);
}
