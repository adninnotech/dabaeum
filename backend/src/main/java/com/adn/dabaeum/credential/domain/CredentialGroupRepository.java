package com.adn.dabaeum.credential.domain;

import java.util.Optional;
import java.util.UUID;

public interface CredentialGroupRepository {

    Optional<CredentialGroup> findById(UUID groupId);

    Optional<CredentialGroup> findByCompletionId(UUID completionId);

    Optional<CredentialGroup> findByCompletionIdForUpdate(UUID completionId);

    void insert(CredentialGroup group);
}
