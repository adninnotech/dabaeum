package com.adn.dabaeum.completion.application;

import java.time.Instant;
import java.util.UUID;

/**
 * 수료 확정·확정 되돌리기 직후, 같은 트랜잭션 안에서 불린다. 학습배지 자동 발급(설계서 §11.2)이 구현한다.
 * 수료 모듈은 구현체를 모른다. 예외를 던지면 확정·되돌리기 자체가 롤백된다.
 */
public interface CompletionConfirmedListener {

    void onCompletionConfirmed(UUID completionId, UUID userId, UUID courseId, Instant confirmedAt);

    /** 관리자가 확정을 되돌린 직후. 이 수료는 이미 COMPLETED 가 아니다. */
    default void onCompletionReverted(UUID completionId, UUID userId, UUID courseId, Instant revertedAt) {
    }
}
