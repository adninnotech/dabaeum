package com.adn.dabaeum.blockchain.api;

import java.time.Instant;
import java.util.UUID;

public record BlockchainTransactionResponse(
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
