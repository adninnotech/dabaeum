package com.adn.dabaeum.blockchain.domain;

import java.time.Instant;
import java.util.Objects;

/** Provider 이력 항목이다. 삭제 이력일 때만 state가 null일 수 있다. */
public record BlockchainHistoryEntry(
    String transactionId,
    CredentialRegistryState state,
    Instant timestamp,
    boolean deleted
) {
    public BlockchainHistoryEntry {
        transactionId = CredentialRegistryState.requireText(transactionId, "transactionId");
        Objects.requireNonNull(timestamp, "timestamp");
        if (deleted && state != null) {
            throw new IllegalArgumentException("deleted history entry must not have state");
        }
        if (!deleted) Objects.requireNonNull(state, "state");
    }
}
