package com.adn.dabaeum.credential.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface CompletionOutboxEventRepository {

    List<CompletionConfirmedOutboxEvent> claimPending(int limit, Instant now);

    void markPublished(UUID eventId, Instant publishedAt);

    void reschedule(UUID eventId, int retryCount, Instant nextRetryAt, String failureCode);

    void markFailed(UUID eventId, int retryCount, String failureCode);
}
