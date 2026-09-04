package com.adn.dabaeum.credential.domain;

/** Credential에 저장된 VC 해시 원문의 버전이다. */
public enum CredentialHashVersion {
    ENVELOPE_SHA256_V0,
    COMPACT_JWS_SHA256_V1
}
