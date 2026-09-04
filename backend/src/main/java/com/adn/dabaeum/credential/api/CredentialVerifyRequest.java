package com.adn.dabaeum.credential.api;

public record CredentialVerifyRequest(
    String credentialNo,
    String credentialHash,
    String verificationType,
    String requesterType,
    String requesterId
) {
}
