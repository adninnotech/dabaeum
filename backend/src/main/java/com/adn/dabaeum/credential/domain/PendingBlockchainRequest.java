package com.adn.dabaeum.credential.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record PendingBlockchainRequest(
    UUID transactionId,
    UUID credentialId,
    String transactionType,
    String network,
    String idempotencyKey,
    String requestHash,
    Instant requestedAt,
    String operationReason
) {

    public PendingBlockchainRequest(
        UUID transactionId, UUID credentialId, String transactionType, String network,
        String idempotencyKey, String requestHash, Instant requestedAt
    ) {
        this(transactionId, credentialId, transactionType, network, idempotencyKey, requestHash,
            requestedAt, null);
    }

    public PendingBlockchainRequest {
        Objects.requireNonNull(transactionId, "transactionId");
        Objects.requireNonNull(credentialId, "credentialId");
        requireText(transactionType, "transactionType");
        requireText(network, "network");
        requireText(idempotencyKey, "idempotencyKey");
        requireText(requestHash, "requestHash");
        Objects.requireNonNull(requestedAt, "requestedAt");
        operationReason = operationReason == null ? null : operationReason.trim();
        if (operationReason != null && operationReason.isBlank()) {
            throw new IllegalArgumentException("operationReason must not be blank");
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
    }
}
