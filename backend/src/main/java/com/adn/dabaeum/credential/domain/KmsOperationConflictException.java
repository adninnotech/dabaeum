package com.adn.dabaeum.credential.domain;

/** 작업 INSERT 가 유니크 제약에 걸렸다 (설계서 §11.4). */
public final class KmsOperationConflictException extends RuntimeException {

    public enum Kind {
        /** 같은 키에 진행 중 작업이 있다 ({@code uq_tb_kms_operations_in_progress}). */
        IN_PROGRESS,
        /** 같은 멱등키의 작업이 있다 ({@code uq_tb_kms_operations_idempotency_key}). */
        IDEMPOTENCY
    }

    private final Kind kind;

    public KmsOperationConflictException(Kind kind) {
        super("kms operation conflict: " + kind);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }
}
