package com.adn.dabaeum.credential.domain;

/** 키 버전 상태 (설계서 §6.1). COMPROMISED 는 어떤 경로로도 해제되지 않는다. */
public enum SignKeyVersionStatus {
    ACTIVE,
    RETIRED,
    COMPROMISED
}
