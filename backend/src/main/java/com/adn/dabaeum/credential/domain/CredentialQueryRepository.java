package com.adn.dabaeum.credential.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CredentialQueryRepository {

    Optional<CredentialCourseView> findCourseByCredentialId(UUID credentialId);

    List<CredentialCourseView> findCoursesByCredentialIds(List<UUID> credentialIds);

    /** 특정 기관들의 과정으로 발급된 수료증만 돌려준다. 기관 관리자가 타 기관 건을 보지 못하게 한다. */
    List<Credential> findByUserIdAndInstitutionIds(
        UUID userId, List<UUID> institutionIds, int limit, int offset, String sort);

    long countByUserIdAndInstitutionIds(UUID userId, List<UUID> institutionIds);
}
