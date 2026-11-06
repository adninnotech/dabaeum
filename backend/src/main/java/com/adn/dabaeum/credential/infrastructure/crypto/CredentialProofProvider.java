package com.adn.dabaeum.credential.infrastructure.crypto;

/** 수료증 서명키의 출처. local 은 파일 키, openbao 는 Transit (ADR-0006). */
public enum CredentialProofProvider {
    LOCAL,
    OPENBAO
}
