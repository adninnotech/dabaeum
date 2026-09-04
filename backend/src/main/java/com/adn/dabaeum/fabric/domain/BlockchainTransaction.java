package com.adn.dabaeum.fabric.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** 비동기로 제출된 단일 Fabric 트랜잭션의 불변 상태이다. */
public record BlockchainTransaction(
    UUID id,
    String referenceType,
    UUID referenceId,
    String network,
    BlockchainTransactionType transactionType,
    String idempotencyKey,
    UUID correlationId,
    String requestHash,
    String transactionId,
    BlockchainTransactionStatus status,
    String errorCode,
    String errorMessage,
    Instant requestedAt,
    Instant confirmedAt,
    int retryCount,
    Instant nextRetryAt,
    String responseMetadata,
    Instant createdAt,
    Instant updatedAt,
    String operationReason
) {
    public static final int MAX_RETRY_COUNT = 5;

    /** Task 8 이전에 생성된 트랜잭션과의 호환성을 위한 생성자이다. */
    public BlockchainTransaction(
        UUID id, String referenceType, UUID referenceId, String network,
        BlockchainTransactionType transactionType, String idempotencyKey, UUID correlationId,
        String requestHash, String transactionId, BlockchainTransactionStatus status,
        String errorCode, String errorMessage, Instant requestedAt, Instant confirmedAt,
        int retryCount, Instant nextRetryAt, String responseMetadata, Instant createdAt,
        Instant updatedAt
    ) {
        this(id, referenceType, referenceId, network, transactionType, idempotencyKey, correlationId,
            requestHash, transactionId, status, errorCode, errorMessage, requestedAt, confirmedAt,
            retryCount, nextRetryAt, responseMetadata, createdAt, updatedAt, null);
    }

    public BlockchainTransaction {
        Objects.requireNonNull(id, "id");
        referenceType = text(referenceType, "referenceType");
        Objects.requireNonNull(referenceId, "referenceId");
        network = text(network, "network");
        Objects.requireNonNull(transactionType, "transactionType");
        idempotencyKey = text(idempotencyKey, "idempotencyKey");
        requestHash = text(requestHash, "requestHash");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(requestedAt, "requestedAt");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        operationReason = operationReason == null ? null : operationReason.trim();
        if (operationReason != null && operationReason.isBlank()) {
            throw new IllegalArgumentException("operationReason must not be blank");
        }
        if (retryCount < 0 || retryCount > MAX_RETRY_COUNT) {
            throw new IllegalArgumentException("retryCount is invalid");
        }
        if (createdAt.isAfter(updatedAt)) {
            throw new IllegalArgumentException("createdAt must not be after updatedAt");
        }
        validateState(status, transactionId, errorCode, confirmedAt, nextRetryAt);
    }

    public BlockchainTransaction claim(Instant now) {
        Objects.requireNonNull(now, "now");
        requireStatus(BlockchainTransactionStatus.PENDING);
        if (nextRetryAt != null && now.isBefore(nextRetryAt)) {
            throw new IllegalStateException("Transaction is not due");
        }
        return copy(BlockchainTransactionStatus.PROCESSING, null, null, null, null, null, now,
            null, operationReason);
    }

    public BlockchainTransaction confirm(String nextTransactionId, String commitCode, Instant now) {
        return confirm(nextTransactionId, commitCode, null, now);
    }

    /** 블록 번호를 알면 response_metadata 에 함께 남겨 원장에서 위치를 추적할 수 있게 한다. */
    public BlockchainTransaction confirm(
        String nextTransactionId, String commitCode, Long blockNumber, Instant now
    ) {
        Objects.requireNonNull(now, "now");
        requireStatus(BlockchainTransactionStatus.PROCESSING);
        return copy(BlockchainTransactionStatus.CONFIRMED, text(nextTransactionId, "transactionId"),
            null, null, now, null, now, commitMetadata(commitCode, blockNumber), operationReason);
    }

    public BlockchainTransaction retry(String nextErrorCode, String nextErrorMessage, Instant retryAt) {
        Objects.requireNonNull(retryAt, "retryAt");
        requireStatus(BlockchainTransactionStatus.PROCESSING);
        int nextCount = retryCount + 1;
        if (nextCount >= MAX_RETRY_COUNT) {
            return new BlockchainTransaction(id, referenceType, referenceId, network, transactionType,
                idempotencyKey, correlationId, requestHash, null, BlockchainTransactionStatus.FAILED,
                text(nextErrorCode, "errorCode"), sanitize(nextErrorMessage), requestedAt, null,
                nextCount, null, null, createdAt, retryAt, operationReason);
        }
        return new BlockchainTransaction(id, referenceType, referenceId, network, transactionType,
            idempotencyKey, correlationId, requestHash, null, BlockchainTransactionStatus.PENDING,
            text(nextErrorCode, "errorCode"), sanitize(nextErrorMessage), requestedAt, null, nextCount,
            retryAt, null, createdAt, retryAt, operationReason);
    }

    public BlockchainTransaction fail(String nextErrorCode, String nextErrorMessage, Instant now) {
        Objects.requireNonNull(now, "now");
        requireStatus(BlockchainTransactionStatus.PROCESSING);
        return copy(BlockchainTransactionStatus.FAILED, null, text(nextErrorCode, "errorCode"),
            sanitize(nextErrorMessage), null, null, now, null, operationReason);
    }

    private BlockchainTransaction copy(
        BlockchainTransactionStatus nextStatus,
        String nextTransactionId,
        String nextErrorCode,
        String nextErrorMessage,
        Instant nextConfirmedAt,
        Instant nextRetryAt,
        Instant nextUpdatedAt
    ) {
        return copy(nextStatus, nextTransactionId, nextErrorCode, nextErrorMessage, nextConfirmedAt,
            nextRetryAt, nextUpdatedAt, null, null);
    }

    private BlockchainTransaction copy(
        BlockchainTransactionStatus nextStatus,
        String nextTransactionId,
        String nextErrorCode,
        String nextErrorMessage,
        Instant nextConfirmedAt,
        Instant nextRetryAt,
        Instant nextUpdatedAt,
        String nextResponseMetadata,
        String nextOperationReason
    ) {
        return new BlockchainTransaction(id, referenceType, referenceId, network, transactionType,
            idempotencyKey, correlationId, requestHash, nextTransactionId, nextStatus, nextErrorCode,
            nextErrorMessage, requestedAt, nextConfirmedAt, retryCount, nextRetryAt,
            nextResponseMetadata, createdAt, nextUpdatedAt, nextOperationReason);
    }

    private void requireStatus(BlockchainTransactionStatus expected) {
        if (status != expected) {
            throw new IllegalStateException("Transaction state conflict");
        }
    }

    private static void validateState(
        BlockchainTransactionStatus state,
        String transactionId,
        String errorCode,
        Instant confirmedAt,
        Instant nextRetryAt
    ) {
        switch (state) {
            case PENDING -> {
                if (transactionId != null || confirmedAt != null) {
                    throw new IllegalArgumentException("Pending transaction has a terminal result");
                }
            }
            case PROCESSING -> {
                if (transactionId != null || errorCode != null || confirmedAt != null || nextRetryAt != null) {
                    throw new IllegalArgumentException("Processing transaction has a result");
                }
            }
            case CONFIRMED -> {
                text(transactionId, "transactionId");
                if (errorCode != null || confirmedAt == null || nextRetryAt != null) {
                    throw new IllegalArgumentException("Confirmed transaction is invalid");
                }
            }
            case FAILED -> {
                text(errorCode, "errorCode");
                if (transactionId != null || confirmedAt != null || nextRetryAt != null) {
                    throw new IllegalArgumentException("Failed transaction is invalid");
                }
            }
        }
    }

    private static String text(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.trim();
    }

    private static String sanitize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String commitMetadata(String commitCode, Long blockNumber) {
        String code = text(commitCode, "commitCode")
            .replace("\\", "\\\\")
            .replace("\"", "\\\"");
        if (blockNumber == null) {
            return "{\"commitCode\":\"" + code + "\"}";
        }
        return "{\"commitCode\":\"" + code + "\",\"blockNumber\":" + blockNumber + "}";
    }
}
