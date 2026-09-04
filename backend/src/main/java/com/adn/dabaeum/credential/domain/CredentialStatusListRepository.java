package com.adn.dabaeum.credential.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CredentialStatusListRepository {

    Optional<CredentialStatusList> findById(UUID listId);

    Optional<CredentialStatusList> findLatestByInstitutionId(UUID institutionId);

    void insert(CredentialStatusList list);

    long countEntries(UUID listId);

    /**
     * 아직 자리가 없는 Credential 에 리스트 칸을 배정한다.
     *
     * @return 배정했으면 true. 칸이 이미 쓰였거나 Credential 에 이미 자리가 있으면 false
     */
    boolean assignEntry(UUID credentialId, CredentialStatusListEntry entry);

    Optional<CredentialStatusListEntry> findEntryByCredentialId(UUID credentialId);

    /** 폐기 비트를 1 로 세워야 하는 인덱스. REVOKED 와 재발급으로 대체된 SUPERSEDED 를 포함한다. */
    List<Integer> findRevokedIndexes(UUID listId);
}
