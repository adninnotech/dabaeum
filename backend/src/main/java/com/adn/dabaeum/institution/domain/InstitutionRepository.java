package com.adn.dabaeum.institution.domain;

import java.util.Collection;
import java.util.Optional;
import java.util.List;
import java.util.UUID;

public interface InstitutionRepository {

    void save(Institution institution);

    Optional<Institution> findById(UUID id);

    List<Institution> findByIds(Collection<UUID> ids);

    Optional<Institution> findActiveById(UUID id);

    Optional<Institution> findActiveByCode(String institutionCode);

    List<InstitutionListItem> findActivePage(InstitutionPageCriteria criteria);

    /** q 검색·status·region 필터가 있는 목록용. findActivePage 의 조건과 반드시 같아야 한다. */
    long countActive(String keyword, InstitutionStatus status, String region);

    boolean updateActive(Institution institution);
}
