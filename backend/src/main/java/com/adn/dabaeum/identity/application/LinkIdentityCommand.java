package com.adn.dabaeum.identity.application;

import com.adn.dabaeum.identity.domain.IdentityProvider;
import java.util.UUID;

public record LinkIdentityCommand(
    UUID userId,
    IdentityProvider provider,
    String providerSubject,
    String externalDid,
    Boolean verified
) {
}
