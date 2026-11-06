package com.adn.dabaeum.credential.domain;

/** 키 관리자 Transit 호출 실패. UNAVAILABLE 은 결과가 불확실할 수 있다 (설계서 §15.1). */
public final class TransitKeyAdminException extends RuntimeException {

    public enum Reason {
        /** 연결 실패·403·429·5xx. 요청이 도달했을 수 있다. */
        UNAVAILABLE,
        /** OpenBao 가 명시적으로 거부했거나 응답을 해석할 수 없다. */
        REJECTED,
        /**
         * AppRole 로그인 실패(Secret ID 만료 등). 키 경로에 닿기 전이라 확실히 미실행이지만, 사람이 자격을 고쳐야
         * 풀린다. 미실행이 확실한 쪽만 이 값을 쓰고, 그 밖의 호출부는 UNAVAILABLE 과 같이 다룬다.
         */
        AUTHENTICATION_FAILED
    }

    private final Reason reason;

    public TransitKeyAdminException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
