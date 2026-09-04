package com.adn.dabaeum.blockchain.domain;

/** Credential 원장 상태와 Storage 호환 코드의 대응이다. */
public enum RegistryStatus {
    ACTIVE("A"),
    REVOKED("R"),
    SUPERSEDED("S");

    private final String code;

    RegistryStatus(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
