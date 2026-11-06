package com.adn.dabaeum.credential.domain;

import java.util.Objects;

/** Transit 서명 실패. 메시지에 토큰·입력 원문을 넣지 않는다. */
public final class TransitSigningException extends RuntimeException {

    public enum Reason {
        /** min_encryption_version 보다 낮은 버전. 같은 행에서 새 버전으로 다시 커밋한다 (§8.1). */
        VERSION_REJECTED,
        /** 봉인·네트워크·5xx·권한 만료. 지수 백오프로 다시 시도한다 (§15.3). */
        UNAVAILABLE,
        /** 키 없음·잘못된 요청·잘못된 응답. 다시 시도해도 같다. */
        REJECTED
    }

    private final Reason reason;

    public TransitSigningException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = Objects.requireNonNull(reason, "reason");
    }

    public Reason reason() {
        return reason;
    }
}
