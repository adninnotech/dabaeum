package com.adn.dabaeum.credential.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * 설계서 §9 2~4단계. 서명 문서의 형식·정규성을 확인하고 kid 로 해석한 버전 행 공개키로 서명을 검증한다. 버전
 * 상태(§6.3)·발급 기록·원장 판정은 호출자가 한다.
 */
public interface CredentialSignatureVerifier {

    /** @throws VerificationFailure 형식·kid·목적·서명이 맞지 않거나 키를 읽을 수 없으면 */
    VerifiedDocument verifyCredential(SignedCredentialEnvelope envelope);

    /** @throws VerificationFailure 형식·kid·목적·서명이 맞지 않거나 키를 읽을 수 없으면 */
    VerifiedDocument verifyStatusList(SignedCredentialEnvelope envelope);

    /** 서명이 맞은 문서, 그 발급 기관, 검증에 쓴 키. */
    record VerifiedDocument(CredentialDocument document, UUID institutionId, VerificationKey key) {

        public VerifiedDocument {
            Objects.requireNonNull(document, "document");
            Objects.requireNonNull(institutionId, "institutionId");
            Objects.requireNonNull(key, "key");
        }
    }

    final class VerificationFailure extends RuntimeException {

        /** 형식·정규 직렬화·서명이 맞지 않는다. 그 밖의 코드는 {@link CredentialVerificationKeyResolver.KeyResolutionException} 상수다. */
        public static final String PROOF_INVALID = "CREDENTIAL_PROOF_INVALID";

        private final String code;

        public VerificationFailure(String code) {
            super(code);
            this.code = Objects.requireNonNull(code, "code");
        }

        public String code() {
            return code;
        }

        /** 위조 판정이 아니라 키를 읽지 못한 경우다. */
        public boolean unavailable() {
            return CredentialVerificationKeyResolver.KeyResolutionException.UNAVAILABLE.equals(code);
        }
    }
}
