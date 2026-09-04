package com.adn.dabaeum.blockchain.domain;

import java.time.Instant;
import java.util.UUID;

public record BlockchainTransactionSummary(
    UUID id,
    String txHash,
    String type,
    String status,
    Instant occurredAt,
    Instant requestedAt,
    Instant confirmedAt,
    Long blockNumber,
    boolean reconciled
) {
}
