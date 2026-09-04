package com.adn.dabaeum.blockchain.domain;

/** Provider 중립 원장 이력 페이지 질의이다. */
public record BlockchainHistoryQuery(int limit, int offset, boolean reverse) {
    public BlockchainHistoryQuery {
        if (limit < 10 || limit > 50) {
            throw new IllegalArgumentException("limit must be between 10 and 50");
        }
        if (offset < 0) {
            throw new IllegalArgumentException("offset must not be negative");
        }
    }
}
