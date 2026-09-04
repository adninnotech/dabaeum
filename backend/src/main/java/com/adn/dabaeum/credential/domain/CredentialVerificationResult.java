package com.adn.dabaeum.credential.domain;

public enum CredentialVerificationResult {
    VALID,
    INVALID,
    REVOKED,
    SUPERSEDED,
    EXPIRED,
    NOT_FOUND,
    ERROR
}
