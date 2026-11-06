package com.adn.dabaeum.credential.domain;

import java.util.Objects;

/**
 * 수료증 서명 실패. 코드는 수료증 failure_code 와 원장 트랜잭션 error_code 에 그대로 쓴다. 처리 방식은 워커가
 * 트랜잭션을 어떻게 닫을지 정한다 (설계서 §15.3).
 */
public final class CredentialSigningException extends RuntimeException {

    public static final String INSTITUTION_INACTIVE = "CREDENTIAL_SIGNING_INSTITUTION_INACTIVE";
    public static final String KEY_INACTIVE = "CREDENTIAL_SIGNING_KEY_INACTIVE";
    public static final String KEY_COMPROMISED = "CREDENTIAL_SIGNING_KEY_COMPROMISED";
    public static final String VERSION_MISMATCH = "CREDENTIAL_SIGNATURE_VERSION_MISMATCH";
    public static final String VERSION_REJECTED = "CREDENTIAL_SIGNING_VERSION_REJECTED";
    public static final String UNAVAILABLE = "CREDENTIAL_SIGNING_UNAVAILABLE";
    public static final String REJECTED = "CREDENTIAL_SIGNING_REJECTED";
    public static final String STALE = "CREDENTIAL_SIGNING_STALE";

    public enum Disposition {
        /** 수료증은 ISSUING 그대로, 원장 트랜잭션은 지수 백오프로 다시 시도한다. */
        RETRY,
        /** 수료증과 원장 트랜잭션을 모두 실패로 닫는다. 원장에는 아무것도 올리지 않았다. */
        FAIL,
        /** 다른 시도가 이 행을 가져갔다. 아무것도 쓰지 않는다. */
        STALE
    }

    private final String code;
    private final Disposition disposition;

    public CredentialSigningException(String code, Disposition disposition) {
        this(code, disposition, null);
    }

    public CredentialSigningException(String code, Disposition disposition, Throwable cause) {
        super(code, cause);
        this.code = Objects.requireNonNull(code, "code");
        this.disposition = Objects.requireNonNull(disposition, "disposition");
    }

    public String code() {
        return code;
    }

    public Disposition disposition() {
        return disposition;
    }
}
