package com.adn.dabaeum.fabric.infrastructure.mybatis;

import java.time.Instant;
import java.util.UUID;

public record PendingBlockchainRequestRow(
    UUID transactionId,
    UUID credentialId,
    String transactionType,
    String network,
    String idempotencyKey,
    String requestHash,
    Instant requestedAt,
    String operationReason
) {
}
