package com.adn.dabaeum.credential.domain;

import java.security.PublicKey;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 서명 검증에 쓸 공개키와 그 버전 행의 상태 (설계서 §9 3~5단계). 버전 행 없이 설정 파일 공개키로 대신한 전환
 * 이전 키는 {@code signKeyId}·{@code keyVersion} 이 없다.
 */
public record VerificationKey(
    PublicKey publicKey,
    String kidFragment,
    UUID signKeyId,
    Integer keyVersion,
    SignKeyVersionStatus status,
    Instant compromisedAt
) {

    public VerificationKey {
        Objects.requireNonNull(publicKey, "publicKey");
        Objects.requireNonNull(kidFragment, "kidFragment");
        Objects.requireNonNull(status, "status");
        if ((signKeyId == null) != (keyVersion == null)) {
            throw new IllegalArgumentException("signKeyId and keyVersion must be given together");
        }
        if (status == SignKeyVersionStatus.COMPROMISED && compromisedAt == null) {
            throw new IllegalArgumentException("compromisedAt is required");
        }
    }

    /** 버전 0 행이 아직 없는(백필 전) 전환 이전 키. 설정 파일 공개키로 대신한다. */
    public static VerificationKey legacyFile(PublicKey publicKey) {
        return new VerificationKey(publicKey, SignKeyNaming.LEGACY_KID_FRAGMENT, null, null,
            SignKeyVersionStatus.RETIRED, null);
    }

    public boolean compromised() {
        return status == SignKeyVersionStatus.COMPROMISED;
    }
}
