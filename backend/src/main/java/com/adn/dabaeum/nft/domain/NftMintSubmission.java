package com.adn.dabaeum.nft.domain;

import java.util.Objects;

/** 발행 요청 결과. fact hash 는 항상 있고, 처리 결과는 대기·확정·거절 중 하나다. */
public record NftMintSubmission(String factHash, NftTransactionState state) {

    public NftMintSubmission {
        if (factHash == null || factHash.isBlank()) {
            throw new IllegalArgumentException("factHash is required");
        }
        Objects.requireNonNull(state, "state");
    }
}
