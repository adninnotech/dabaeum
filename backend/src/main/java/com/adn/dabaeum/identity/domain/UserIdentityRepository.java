package com.adn.dabaeum.identity.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserIdentityRepository {

    void save(UserIdentity identity);

    Optional<UserIdentity> findById(UUID id);

    Optional<UserIdentity> findByProviderSubject(
        IdentityProvider provider,
        String providerSubject
    );

    List<UserIdentity> findByUserId(UUID userId);

    boolean deleteByIdAndUserId(UUID identityId, UUID userId);
}
