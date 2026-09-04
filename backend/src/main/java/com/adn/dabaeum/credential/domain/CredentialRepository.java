package com.adn.dabaeum.credential.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CredentialRepository {

    Optional<Credential> findById(UUID credentialId);

    Optional<Credential> findByCredentialNo(String credentialNo);

    Optional<Credential> findByCredentialHash(String credentialHash);

    Optional<Credential> findByIdForUpdate(UUID credentialId);

    Optional<Credential> findActiveByGroupId(UUID groupId);

    List<Credential> findByUserId(UUID userId, int limit, int offset, String sort);

    long countByUserId(UUID userId);

    int nextVersionForUpdate(UUID groupId);

    void insert(Credential credential);

    boolean insertIfChainKeyAvailable(Credential credential);

    void update(Credential credential);
}
