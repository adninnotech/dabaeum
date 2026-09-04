package com.adn.dabaeum.credential.domain;

import java.util.Objects;

/**
 * Credential 과 그 발급 근거가 된 과정 정보를 함께 노출하는 조회 전용 표현이다.
 * 원장에 앵커되는 VC payload 는 식별자만 담아야 하므로 과정명은 이 조회 경로에서만 다룬다.
 */
public record CredentialView(
    Credential credential,
    CredentialCourseView course
) {

    public CredentialView {
        Objects.requireNonNull(credential, "credential");
    }
}
