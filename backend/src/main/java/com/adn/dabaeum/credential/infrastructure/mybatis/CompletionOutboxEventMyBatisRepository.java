package com.adn.dabaeum.credential.infrastructure.mybatis;

import com.adn.dabaeum.credential.domain.CompletionConfirmedOutboxEvent;
import com.adn.dabaeum.credential.domain.CompletionOutboxEventRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile({"local", "dev"})
public class CompletionOutboxEventMyBatisRepository implements CompletionOutboxEventRepository {

    private final CompletionOutboxEventMapper mapper;

    public CompletionOutboxEventMyBatisRepository(CompletionOutboxEventMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<CompletionConfirmedOutboxEvent> claimPending(int limit, Instant now) {
        return mapper.selectPendingForUpdate(limit, now).stream()
            .map(row -> new CompletionConfirmedOutboxEvent(
                row.id(), row.aggregateId(), row.payloadJson(), row.retryCount()))
            .toList();
    }

    @Override
    public void markPublished(UUID eventId, Instant publishedAt) {
        mapper.markPublished(eventId, publishedAt);
    }

    @Override
    public void reschedule(UUID eventId, int retryCount, Instant nextRetryAt, String failureCode) {
        mapper.reschedule(eventId, retryCount, nextRetryAt, failureCode);
    }

    @Override
    public void markFailed(UUID eventId, int retryCount, String failureCode) {
        mapper.markFailed(eventId, retryCount, failureCode);
    }
}
