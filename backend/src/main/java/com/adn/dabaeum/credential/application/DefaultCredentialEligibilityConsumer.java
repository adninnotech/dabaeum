package com.adn.dabaeum.credential.application;

import com.adn.dabaeum.completion.domain.Completion;
import com.adn.dabaeum.completion.domain.CompletionRepository;
import com.adn.dabaeum.completion.domain.CompletionStatus;
import com.adn.dabaeum.credential.domain.CompletionConfirmedOutboxEvent;
import com.adn.dabaeum.credential.domain.CompletionOutboxEventRepository;
import com.adn.dabaeum.credential.domain.CredentialGroup;
import com.adn.dabaeum.credential.domain.CredentialGroupRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.NoTransactionException;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
@Profile({"local", "dev"})
public class DefaultCredentialEligibilityConsumer implements CredentialEligibilityConsumer {

    private static final int MAX_RETRIES = 5;
    private static final long INITIAL_RETRY_DELAY_SECONDS = 30;
    private static final long MAX_RETRY_DELAY_SECONDS = 30 * 60;

    private final CompletionOutboxEventRepository outboxRepository;
    private final CompletionRepository completionRepository;
    private final CredentialGroupRepository groupRepository;
    private final CredentialGroupIdGenerator groupIdGenerator;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public DefaultCredentialEligibilityConsumer(
        CompletionOutboxEventRepository outboxRepository,
        CompletionRepository completionRepository,
        CredentialGroupRepository groupRepository,
        CredentialGroupIdGenerator groupIdGenerator,
        ObjectMapper objectMapper,
        Clock clock
    ) {
        this.outboxRepository = Objects.requireNonNull(outboxRepository);
        this.completionRepository = Objects.requireNonNull(completionRepository);
        this.groupRepository = Objects.requireNonNull(groupRepository);
        this.groupIdGenerator = Objects.requireNonNull(groupIdGenerator);
        this.objectMapper = Objects.requireNonNull(objectMapper);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    @Transactional
    public int consumeBatch(int limit, Instant now) {
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be positive");
        }
        Objects.requireNonNull(now, "now");

        List<CompletionConfirmedOutboxEvent> events = outboxRepository.claimPending(limit, now);
        TransactionStatus transactionStatus = currentTransactionOrNull();
        for (CompletionConfirmedOutboxEvent event : events) {
            processWithSavepoint(event, transactionStatus);
        }
        return events.size();
    }

    private void processWithSavepoint(
        CompletionConfirmedOutboxEvent event,
        TransactionStatus transactionStatus
    ) {
        Object savepoint = transactionStatus == null
            ? null : transactionStatus.createSavepoint();
        try {
            consume(event);
            releaseSavepoint(transactionStatus, savepoint);
        } catch (PermanentEventException exception) {
            outboxRepository.markFailed(event.id(), event.retryCount(),
                exception.failureCode.name());
            releaseSavepoint(transactionStatus, savepoint);
        } catch (RuntimeException exception) {
            rollbackToSavepoint(transactionStatus, savepoint);
            rescheduleOrFail(event, clock.instant());
            releaseSavepoint(transactionStatus, savepoint);
        }
    }

    private void rollbackToSavepoint(TransactionStatus transactionStatus, Object savepoint) {
        if (savepoint != null) {
            transactionStatus.rollbackToSavepoint(savepoint);
        }
    }

    private void releaseSavepoint(TransactionStatus transactionStatus, Object savepoint) {
        if (savepoint != null) {
            transactionStatus.releaseSavepoint(savepoint);
        }
    }

    private void consume(CompletionConfirmedOutboxEvent event) {
        CompletionConfirmedPayload payload = parsePayload(event.payloadJson());
        if (!event.aggregateId().equals(payload.completionId())) {
            throw permanent(FailureCode.COMPLETION_OUTBOX_COMPLETION_MISMATCH);
        }

        Completion completion = completionRepository
            .findByEnrollmentId(payload.enrollmentId())
            .orElseThrow(() -> permanent(FailureCode.COMPLETION_NOT_CONFIRMED));
        if (!completion.id().equals(payload.completionId())) {
            throw permanent(FailureCode.COMPLETION_OUTBOX_COMPLETION_MISMATCH);
        }
        if (completion.status() != CompletionStatus.COMPLETED) {
            throw permanent(FailureCode.COMPLETION_NOT_CONFIRMED);
        }

        Instant processedAt = clock.instant();
        if (groupRepository.findByCompletionIdForUpdate(payload.completionId()).isEmpty()) {
            groupRepository.insert(new CredentialGroup(
                groupIdGenerator.generate(), payload.completionId(), processedAt));
        }
        outboxRepository.markPublished(event.id(), processedAt);
    }

    private TransactionStatus currentTransactionOrNull() {
        try {
            return TransactionAspectSupport.currentTransactionStatus();
        } catch (NoTransactionException exception) {
            return null;
        }
    }

    private CompletionConfirmedPayload parsePayload(String payloadJson) {
        try {
            JsonNode root = objectMapper.readTree(payloadJson);
            if (root == null || !root.isObject() || root.size() != 3
                || !hasOnlyExpectedFields(root)) {
                throw permanent(FailureCode.COMPLETION_OUTBOX_PAYLOAD_INVALID);
            }
            return new CompletionConfirmedPayload(
                requiredUuid(root, "completionId"),
                requiredUuid(root, "enrollmentId"),
                requiredUuid(root, "courseId"));
        } catch (PermanentEventException exception) {
            throw exception;
        } catch (Exception exception) {
            throw permanent(FailureCode.COMPLETION_OUTBOX_PAYLOAD_INVALID);
        }
    }

    private boolean hasOnlyExpectedFields(JsonNode root) {
        for (String name : root.propertyNames()) {
            if (!name.equals("completionId") && !name.equals("enrollmentId") && !name.equals("courseId")) {
                return false;
            }
        }
        return true;
    }

    private UUID requiredUuid(JsonNode root, String fieldName) {
        JsonNode value = root.get(fieldName);
        if (value == null || !value.isString() || value.asString().isBlank()) {
            throw permanent(FailureCode.COMPLETION_OUTBOX_PAYLOAD_INVALID);
        }
        try {
            return UUID.fromString(value.asString());
        } catch (IllegalArgumentException exception) {
            throw permanent(FailureCode.COMPLETION_OUTBOX_PAYLOAD_INVALID);
        }
    }

    private void rescheduleOrFail(CompletionConfirmedOutboxEvent event, Instant now) {
        int nextRetryCount = Math.min(event.retryCount() + 1, MAX_RETRIES);
        if (nextRetryCount >= MAX_RETRIES) {
            outboxRepository.markFailed(event.id(), nextRetryCount,
                FailureCode.COMPLETION_OUTBOX_PROCESSING_FAILED.name());
            return;
        }
        long delaySeconds = Math.min(
            (1L << event.retryCount()) * INITIAL_RETRY_DELAY_SECONDS,
            MAX_RETRY_DELAY_SECONDS);
        outboxRepository.reschedule(event.id(), nextRetryCount, now.plusSeconds(delaySeconds),
            FailureCode.COMPLETION_OUTBOX_PROCESSING_FAILED.name());
    }

    private PermanentEventException permanent(FailureCode failureCode) {
        return new PermanentEventException(failureCode);
    }

    private record CompletionConfirmedPayload(
        UUID completionId,
        UUID enrollmentId,
        UUID courseId
    ) {
    }

    private enum FailureCode {
        COMPLETION_NOT_CONFIRMED,
        COMPLETION_OUTBOX_COMPLETION_MISMATCH,
        COMPLETION_OUTBOX_PAYLOAD_INVALID,
        COMPLETION_OUTBOX_PROCESSING_FAILED
    }

    private static final class PermanentEventException extends RuntimeException {
        private final FailureCode failureCode;

        private PermanentEventException(FailureCode failureCode) {
            this.failureCode = failureCode;
        }
    }
}
