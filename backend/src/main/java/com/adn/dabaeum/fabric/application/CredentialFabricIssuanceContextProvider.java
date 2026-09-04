package com.adn.dabaeum.fabric.application;

import java.time.Instant;
import java.util.UUID;

@FunctionalInterface
public interface CredentialFabricIssuanceContextProvider {
    CredentialFabricIssuanceContext resolve(UUID credentialId, Instant issuedAt);
}
