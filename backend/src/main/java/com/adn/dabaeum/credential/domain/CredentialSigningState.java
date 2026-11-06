package com.adn.dabaeum.credential.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * 수료증 행의 서명 컬럼 (설계서 §7.4, §11.3). 계획 2 백필 행은 키 참조와 시도 번호가 있어도 헤더·payload 가
 * 비어 있을 수 있으므로, 입력 커밋 여부는 네 값이 모두 있는지로 판단한다.
 */
public record CredentialSigningState(
    UUID credentialId,
    CredentialStatus status,
    UUID signKeyId,
    Integer signKeyVersion,
    Integer signAttemptNo,
    String vcJwsHeader,
    String vcJwsPayload,
    String vcPayload
) {

    public CredentialSigningState {
        Objects.requireNonNull(credentialId, "credentialId");
        Objects.requireNonNull(status, "status");
        if ((signKeyId == null) != (signKeyVersion == null)) {
            throw new IllegalStateException("signKeyId and signKeyVersion must be stored together");
        }
    }

    public boolean inputCommitted() {
        return signKeyId != null && signAttemptNo != null && vcJwsHeader != null && vcJwsPayload != null;
    }

    public CredentialSigningInput input() {
        if (!inputCommitted()) {
            throw new IllegalStateException("signing input is not committed");
        }
        return new CredentialSigningInput(vcJwsHeader, vcJwsPayload);
    }
}
