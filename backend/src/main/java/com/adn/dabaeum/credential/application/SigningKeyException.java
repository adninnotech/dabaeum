package com.adn.dabaeum.credential.application;

import com.adn.dabaeum.credential.domain.KmsOperationConflictException;
import java.util.Objects;
import java.util.UUID;

/** 키 관리 서비스의 거부·실패. 컨트롤러가 HTTP 상태와 오류 코드로 옮긴다. */
public final class SigningKeyException extends RuntimeException {

    public enum Kind {
        NOT_FOUND,
        ALREADY_EXISTS,
        STATE_CONFLICT,
        APPROVAL_REJECTED,
        IDEMPOTENCY_CONFLICT,
        KMS_UNAVAILABLE
    }

    private final Kind kind;
    private final UUID operationId;

    public SigningKeyException(Kind kind, String message) {
        this(kind, message, null);
    }

    public SigningKeyException(Kind kind, String message, UUID operationId) {
        super(Objects.requireNonNull(message, "message"));
        this.kind = Objects.requireNonNull(kind, "kind");
        this.operationId = operationId;
    }

    public Kind kind() {
        return kind;
    }

    /** 실패가 남긴 작업 기록. 없으면 null. */
    public UUID operationId() {
        return operationId;
    }

    /** 저장소 유니크 충돌을 API 거부로 옮긴다. Task 4·5·6 이 같은 매핑을 쓴다. */
    public static SigningKeyException from(KmsOperationConflictException exception) {
        return switch (exception.kind()) {
            case IN_PROGRESS -> new SigningKeyException(Kind.STATE_CONFLICT,
                "another operation is in progress for this key");
            case IDEMPOTENCY -> new SigningKeyException(Kind.IDEMPOTENCY_CONFLICT,
                "idempotency key was already used");
        };
    }
}
