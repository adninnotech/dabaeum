package com.adn.dabaeum.blockchain.domain;

import java.time.Instant;
import java.util.Objects;

/** Provider 고유 응답을 Business Layer 공통 형태로 정규화한 영수증이다. */
public record BlockchainReceipt(
    BlockchainProvider provider,
    String transactionId,
    String factHash,
    Long blockHeight,
    Instant confirmedAt,
    boolean confirmed,
    String resultCode
) {
    public BlockchainReceipt {
        Objects.requireNonNull(provider, "provider");
        transactionId = CredentialRegistryState.requireText(transactionId, "transactionId");
        if (factHash != null && factHash.isBlank()) {
            throw new IllegalArgumentException("factHash must be null or non-blank");
        }
        if (blockHeight != null && blockHeight < 0) {
            throw new IllegalArgumentException("blockHeight must not be negative");
        }
        if (!confirmed && confirmedAt != null) {
            throw new IllegalArgumentException("unconfirmed receipt must not have confirmedAt");
        }
        resultCode = CredentialRegistryState.requireText(resultCode, "resultCode");
    }
}
