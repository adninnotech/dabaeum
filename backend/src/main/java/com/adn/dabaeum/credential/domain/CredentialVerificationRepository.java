package com.adn.dabaeum.credential.domain;

import java.util.List;
import java.util.UUID;

public interface CredentialVerificationRepository {

    void insert(CredentialVerification verification);

    List<CredentialVerification> findByCredentialId(
        UUID credentialId, int limit, int offset, String sort);

    long countByCredentialId(UUID credentialId);
}
