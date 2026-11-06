package com.adn.dabaeum.nft.domain;

import java.time.Instant;
import java.util.Objects;

/** 체인 처리 상태. 확정이면 높이·시각, 거절이면 사유를 담는다. */
public record NftTransactionState(NftTransactionStatus status, Long height, Instant confirmedAt, String reason) {

    public NftTransactionState {
        Objects.requireNonNull(status, "status");
    }

    public static NftTransactionState pending() {
        return new NftTransactionState(NftTransactionStatus.PENDING, null, null, null);
    }

    public static NftTransactionState confirmed(long height, Instant confirmedAt) {
        return new NftTransactionState(NftTransactionStatus.CONFIRMED, height, confirmedAt, null);
    }

    public static NftTransactionState rejected(String reason) {
        return new NftTransactionState(NftTransactionStatus.REJECTED, null, null,
            reason == null || reason.isBlank() ? "REJECTED" : reason);
    }
}
