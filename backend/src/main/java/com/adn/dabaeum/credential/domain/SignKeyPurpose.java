package com.adn.dabaeum.credential.domain;

/** 키 용도. 수료증 서명과 Status List 서명은 다른 Transit mount 의 다른 키다 (설계서 §5.1). */
public enum SignKeyPurpose {
    CREDENTIAL("education-sign"),
    STATUS_LIST("education-status");

    private final String mount;

    SignKeyPurpose(String mount) {
        this.mount = mount;
    }

    public String mount() {
        return mount;
    }
}
