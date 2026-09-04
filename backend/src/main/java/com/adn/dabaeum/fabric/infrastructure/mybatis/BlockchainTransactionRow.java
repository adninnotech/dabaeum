package com.adn.dabaeum.fabric.infrastructure.mybatis;

import java.time.Instant;
import java.util.UUID;

public record BlockchainTransactionRow(
    UUID id, String referenceType, UUID referenceId, String network, String transactionType,
    String idempotencyKey, UUID correlationId, String requestHash, String transactionId,
    String status, String errorCode, String errorMessage, Instant requestedAt, Instant confirmedAt,
    Integer retryCount, Instant nextRetryAt, String responseMetadata, Instant createdAt, Instant updatedAt,
    String operationReason
) { }
