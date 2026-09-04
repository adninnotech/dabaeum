package com.adn.dabaeum.blockchain.infrastructure.mybatis;

import java.time.Instant;
import java.util.UUID;

public record BlockchainTransactionSummaryRow(
    UUID id,
    String txHash,
    String type,
    String status,
    Instant occurredAt,
    Instant requestedAt,
    Instant confirmedAt,
    Long blockNumber,
    Boolean reconciled
) {
}
