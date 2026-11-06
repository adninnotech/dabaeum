package com.adn.dabaeum.credential.application;

import com.adn.dabaeum.credential.domain.InstitutionSignKey;
import com.adn.dabaeum.credential.domain.InstitutionSignKeyVersion;
import java.util.Objects;

/** 서명에 쓸 현재 세대 마스터, 현재 버전 행, 그 버전으로 만든 헤더 kid. */
public record SigningKeySelection(
    InstitutionSignKey master,
    InstitutionSignKeyVersion version,
    String keyId
) {

    public SigningKeySelection {
        Objects.requireNonNull(master, "master");
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(keyId, "keyId");
        if (!version.signKeyId().equals(master.id()) || version.keyVersion() != master.currentVersion()) {
            throw new IllegalArgumentException("version must be the master's current version");
        }
    }
}
