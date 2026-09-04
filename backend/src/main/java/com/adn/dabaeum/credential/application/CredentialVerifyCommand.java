package com.adn.dabaeum.credential.application;

import java.time.Instant;

public record CredentialVerifyCommand(
    String credentialNo,
    String credentialHash,
    String verificationType,
    String requesterType,
    String requestId,
    Instant requestedAt
) {
}
