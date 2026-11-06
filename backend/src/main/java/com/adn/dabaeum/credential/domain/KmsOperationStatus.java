package com.adn.dabaeum.credential.domain;

/** 작업 상태 (설계서 §15.1). 진행 중 셋은 같은 키의 새 작업을 막는다 (§11.4 부분 유니크). */
public enum KmsOperationStatus {
    PREPARED,
    UNCONFIRMED,
    RECORDED,
    COMPLETED,
    FAILED;

    public boolean inProgress() {
        return this == PREPARED || this == UNCONFIRMED || this == RECORDED;
    }
}
