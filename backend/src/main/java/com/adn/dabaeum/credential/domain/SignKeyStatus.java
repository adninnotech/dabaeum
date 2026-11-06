package com.adn.dabaeum.credential.domain;

/** 키 마스터 상태 (설계서 §6.1). */
public enum SignKeyStatus {
    PROVISIONING,
    ACTIVE,
    SUSPENDED,
    REVOKED,
    COMPROMISED,
    ERROR
}
