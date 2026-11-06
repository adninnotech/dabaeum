package com.adn.dabaeum.nft.application;

import java.time.Duration;
import java.time.Instant;

/**
 * 체인에 아무것도 생기지 않은 실패(연결 실패, 잔액 부족 등)만 다시 보낸다. 대기 시간은 시도마다 두 배.
 *
 * @param maxAttempts 이 횟수만큼 보내고도 실패하면 운영자 확인으로 넘긴다
 */
public record NftRetryPolicy(int maxAttempts, Duration baseDelay) {

    public NftRetryPolicy {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be positive");
        }
        if (baseDelay == null || baseDelay.isNegative() || baseDelay.isZero()) {
            throw new IllegalArgumentException("baseDelay must be positive");
        }
    }

    /** 방금 실패한 시도가 {@code attempt} 번째일 때 다시 보낼 수 있는가. */
    public boolean canRetryAfter(int attempt) {
        return attempt < maxAttempts;
    }

    public Instant nextAttempt(int attempt, Instant now) {
        long factor = 1L << Math.min(attempt - 1, 16);
        return now.plus(baseDelay.multipliedBy(factor));
    }
}
