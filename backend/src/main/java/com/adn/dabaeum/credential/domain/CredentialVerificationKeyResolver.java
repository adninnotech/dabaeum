package com.adn.dabaeum.credential.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * 설계서 §9 3단계. 서명 문서의 발급 기관과 kid 조각으로 버전 행을 찾아 목적을 대조하고 보존 공개키를 돌려준다.
 * 버전 상태(§6.3) 판정은 호출자가 한다.
 */
public interface CredentialVerificationKeyResolver {

    enum DocumentKind {
        CREDENTIAL,
        STATUS_LIST
    }

    /**
     * @throws KeyResolutionException {@link KeyResolutionException#ISSUER_MISMATCH},
     *     {@link KeyResolutionException#PURPOSE_MISMATCH}, {@link KeyResolutionException#UNAVAILABLE}
     */
    VerificationKey resolve(UUID institutionId, String kidFragment, DocumentKind kind);

    final class KeyResolutionException extends RuntimeException {

        /** 그 기관에 그 kid 조각의 키가 없다. */
        public static final String ISSUER_MISMATCH = "KEY_ISSUER_MISMATCH";
        /** 키의 목적(수료증·Status List)이 문서 종류와 다르다. */
        public static final String PURPOSE_MISMATCH = "KEY_PURPOSE_MISMATCH";
        /** 키를 읽을 수 없다. 위조 판정이 아니라 일시·설정 문제다. */
        public static final String UNAVAILABLE = "KEY_UNAVAILABLE";

        private final String code;

        public KeyResolutionException(String code) {
            super(code);
            this.code = Objects.requireNonNull(code, "code");
        }

        public String code() {
            return code;
        }
    }
}
