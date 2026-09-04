package com.adn.dabaeum.credential.domain;

import java.util.Objects;
import java.util.UUID;

public record CompletionConfirmedOutboxEvent(
    UUID id,
    UUID aggregateId,
    String payloadJson,
    int retryCount
) {

    public CompletionConfirmedOutboxEvent {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(aggregateId, "aggregateId");
        if (payloadJson == null || payloadJson.isBlank()) {
            throw new IllegalArgumentException("payloadJson must not be blank");
        }
        if (retryCount < 0) {
            throw new IllegalArgumentException("retryCount must not be negative");
        }
    }
}
