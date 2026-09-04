package com.adn.dabaeum.credential.domain;

public enum CredentialStatus {
    PENDING,
    ISSUING,
    ISSUED,
    FAILED,
    REVOKED,
    SUPERSEDED,
    EXPIRED
}
