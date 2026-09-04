package com.adn.dabaeum.credential.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.adn.dabaeum.completion.domain.Completion;
import com.adn.dabaeum.completion.domain.CompletionRepository;
import com.adn.dabaeum.completion.domain.CompletionStatus;
import com.adn.dabaeum.credential.domain.CompletionConfirmedOutboxEvent;
import com.adn.dabaeum.credential.domain.CompletionOutboxEventRepository;
import com.adn.dabaeum.credential.domain.CredentialGroup;
import com.adn.dabaeum.credential.domain.CredentialGroupRepository;
import com.adn.dabaeum.credential.domain.CredentialRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class CredentialEligibilityConsumerTest {

    private static final Instant NOW = Instant.parse("2026-08-05T00:00:00Z");
    private static final UUID EVENT_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID COMPLETION_ID = UUID.fromString("10000000-0000-0000-0000-000000000002");
    private static final UUID ENROLLMENT_ID = UUID.fromString("10000000-0000-0000-0000-000000000003");
    private static final UUID COURSE_ID = UUID.fromString("10000000-0000-0000-0000-000000000004");
    private static final UUID GROUP_ID = UUID.fromString("10000000-0000-0000-0000-000000000005");

    @Test
    void createsOneGroupAndPublishesAConfirmedCompletionEvent() {
        Fakes fakes = fakes(event(0, payload(COMPLETION_ID, ENROLLMENT_ID, COURSE_ID)));
        fakes.completions.byEnrollment.put(ENROLLMENT_ID, completed(COMPLETION_ID, ENROLLMENT_ID));

        int consumed = consumer(fakes).consumeBatch(10, NOW);

        assertThat(consumed).isEqualTo(1);
        assertThat(fakes.groups.inserted).containsExactly(
            new CredentialGroup(GROUP_ID, COMPLETION_ID, NOW));
        assertThat(fakes.outbox.published).containsExactly(EVENT_ID);
        assertThat(fakes.outbox.rescheduled).isEmpty();
        assertThat(fakes.outbox.failed).isEmpty();
    }

    @Test
    void publishesWithoutAnotherGroupWhenTheCompletionAlreadyHasOne() {
        Fakes fakes = fakes(event(0, payload(COMPLETION_ID, ENROLLMENT_ID, COURSE_ID)));
        fakes.completions.byEnrollment.put(ENROLLMENT_ID, completed(COMPLETION_ID, ENROLLMENT_ID));
        fakes.groups.byCompletion.put(COMPLETION_ID,
            new CredentialGroup(UUID.randomUUID(), COMPLETION_ID, NOW.minusSeconds(1)));

        consumer(fakes).consumeBatch(10, NOW);

        assertThat(fakes.groups.inserted).isEmpty();
        assertThat(fakes.outbox.published).containsExactly(EVENT_ID);
    }

    @Test
    void marksFailedWhenPayloadCompletionDoesNotMatchTheRereadCompletion() {
        Fakes fakes = fakes(event(0, payload(COMPLETION_ID, ENROLLMENT_ID, COURSE_ID)));
        fakes.completions.byEnrollment.put(ENROLLMENT_ID,
            completed(UUID.randomUUID(), ENROLLMENT_ID));

        consumer(fakes).consumeBatch(10, NOW);

        assertThat(fakes.groups.inserted).isEmpty();
        assertThat(fakes.outbox.failed).containsExactly(
            new Failed(EVENT_ID, 0, "COMPLETION_OUTBOX_COMPLETION_MISMATCH"));
    }

    @Test
    void marksFailedWhenAggregateIdDoesNotMatchPayloadCompletionId() {
        Fakes fakes = fakes(event(UUID.randomUUID(), 0,
            payload(COMPLETION_ID, ENROLLMENT_ID, COURSE_ID)));
        fakes.completions.byEnrollment.put(ENROLLMENT_ID, completed(COMPLETION_ID, ENROLLMENT_ID));

        consumer(fakes).consumeBatch(10, NOW);

        assertThat(fakes.groups.inserted).isEmpty();
        assertThat(fakes.outbox.failed).containsExactly(
            new Failed(EVENT_ID, 0, "COMPLETION_OUTBOX_COMPLETION_MISMATCH"));
    }

    @Test
    void marksFailedWhenThePayloadEnrollmentHasNoCompletion() {
        Fakes fakes = fakes(event(0, payload(COMPLETION_ID, ENROLLMENT_ID, COURSE_ID)));

        consumer(fakes).consumeBatch(10, NOW);

        assertThat(fakes.outbox.failed).containsExactly(
            new Failed(EVENT_ID, 0, "COMPLETION_NOT_CONFIRMED"));
    }

    @Test
    void marksFailedWhenTheRereadCompletionIsNotCompleted() {
        Fakes fakes = fakes(event(0, payload(COMPLETION_ID, ENROLLMENT_ID, COURSE_ID)));
        fakes.completions.byEnrollment.put(ENROLLMENT_ID, eligible(COMPLETION_ID, ENROLLMENT_ID));

        consumer(fakes).consumeBatch(10, NOW);

        assertThat(fakes.outbox.failed).containsExactly(
            new Failed(EVENT_ID, 0, "COMPLETION_NOT_CONFIRMED"));
    }

    @Test
    void marksFailedForMalformedOrUnknownPayloadFieldsWithoutSavingPayloadText() {
        Fakes fakes = fakes(event(0, "{\"completionId\":\"" + COMPLETION_ID
            + "\",\"enrollmentId\":\"" + ENROLLMENT_ID
            + "\",\"courseId\":\"" + COURSE_ID + "\",\"unexpected\":true}"));

        consumer(fakes).consumeBatch(10, NOW);

        assertThat(fakes.outbox.failed).containsExactly(
            new Failed(EVENT_ID, 0, "COMPLETION_OUTBOX_PAYLOAD_INVALID"));
        assertThat(fakes.outbox.failureCodes()).allMatch(code -> code.length() <= 1000)
            .allMatch(code -> !code.contains("{"));
    }

    @Test
    void marksFailedForMalformedOrMissingRequiredPayloadUuid() {
        Fakes malformed = fakes(event(0, "{not-json"));
        Fakes missing = fakes(event(0, "{\"completionId\":\"" + COMPLETION_ID + "\"}"));

        consumer(malformed).consumeBatch(10, NOW);
        consumer(missing).consumeBatch(10, NOW);

        assertThat(malformed.outbox.failed).containsExactly(
            new Failed(EVENT_ID, 0, "COMPLETION_OUTBOX_PAYLOAD_INVALID"));
        assertThat(missing.outbox.failed).containsExactly(
            new Failed(EVENT_ID, 0, "COMPLETION_OUTBOX_PAYLOAD_INVALID"));
    }

    @Test
    void reschedulesTransientFailuresWithBoundedExponentialBackoff() {
        Fakes fakes = fakes(event(0, payload(COMPLETION_ID, ENROLLMENT_ID, COURSE_ID)));
        fakes.completions.failure = new IllegalStateException("database details must not persist");

        consumer(fakes).consumeBatch(10, NOW);

        assertThat(fakes.outbox.rescheduled).containsExactly(
            new Rescheduled(EVENT_ID, 1, NOW.plusSeconds(30), "COMPLETION_OUTBOX_PROCESSING_FAILED"));
        assertThat(fakes.outbox.failed).isEmpty();
    }

    @Test
    void marksFailedAfterTheFifthTransientFailure() {
        Fakes fakes = fakes(event(4, payload(COMPLETION_ID, ENROLLMENT_ID, COURSE_ID)));
        fakes.completions.failure = new IllegalStateException("database details must not persist");

        consumer(fakes).consumeBatch(10, NOW);

        assertThat(fakes.outbox.failed).containsExactly(
            new Failed(EVENT_ID, 5, "COMPLETION_OUTBOX_PROCESSING_FAILED"));
        assertThat(fakes.outbox.rescheduled).isEmpty();
    }

    @Test
    void hasNoCredentialOrFabricDependencyThatCouldIssueAutomatically() {
        assertThat(DefaultCredentialEligibilityConsumer.class.getDeclaredConstructors())
            .allSatisfy(constructor -> assertThat(constructor.getParameterTypes())
                .doesNotContain(CredentialRepository.class)
                .allMatch(type -> !type.getPackageName().startsWith("com.adn.dabaeum.fabric")));
    }

    private CredentialEligibilityConsumer consumer(Fakes fakes) {
        return new DefaultCredentialEligibilityConsumer(
            fakes.outbox, fakes.completions, fakes.groups, () -> GROUP_ID,
            new ObjectMapper(), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private Fakes fakes(CompletionConfirmedOutboxEvent event) {
        return new Fakes(event);
    }

    private CompletionConfirmedOutboxEvent event(int retryCount, String payload) {
        return event(COMPLETION_ID, retryCount, payload);
    }

    private CompletionConfirmedOutboxEvent event(
        UUID aggregateId,
        int retryCount,
        String payload
    ) {
        return new CompletionConfirmedOutboxEvent(EVENT_ID, aggregateId, payload, retryCount);
    }

    private String payload(UUID completionId, UUID enrollmentId, UUID courseId) {
        return "{\"completionId\":\"" + completionId
            + "\",\"enrollmentId\":\"" + enrollmentId
            + "\",\"courseId\":\"" + courseId + "\"}";
    }

    private Completion completed(UUID completionId, UUID enrollmentId) {
        return new Completion(completionId, enrollmentId, CompletionStatus.COMPLETED,
            new BigDecimal("100.00"), 60, null, NOW, NOW, UUID.randomUUID(), NOW,
            null, NOW, NOW);
    }

    private Completion eligible(UUID completionId, UUID enrollmentId) {
        return new Completion(completionId, enrollmentId, CompletionStatus.ELIGIBLE,
            new BigDecimal("100.00"), 60, null, NOW, null, null, null, null, NOW, NOW);
    }

    private static final class Fakes {
        private final FakeOutboxRepository outbox;
        private final FakeCompletionRepository completions = new FakeCompletionRepository();
        private final FakeCredentialGroupRepository groups = new FakeCredentialGroupRepository();

        private Fakes(CompletionConfirmedOutboxEvent event) {
            this.outbox = new FakeOutboxRepository(List.of(event));
        }
    }

    private static final class FakeOutboxRepository implements CompletionOutboxEventRepository {
        private final List<CompletionConfirmedOutboxEvent> events;
        private final List<UUID> published = new ArrayList<>();
        private final List<Rescheduled> rescheduled = new ArrayList<>();
        private final List<Failed> failed = new ArrayList<>();

        private FakeOutboxRepository(List<CompletionConfirmedOutboxEvent> events) {
            this.events = events;
        }

        @Override
        public List<CompletionConfirmedOutboxEvent> claimPending(int limit, Instant now) {
            return events.stream().limit(limit).toList();
        }

        @Override
        public void markPublished(UUID eventId, Instant publishedAt) {
            published.add(eventId);
        }

        @Override
        public void reschedule(UUID eventId, int retryCount, Instant nextRetryAt, String failureCode) {
            rescheduled.add(new Rescheduled(eventId, retryCount, nextRetryAt, failureCode));
        }

        @Override
        public void markFailed(UUID eventId, int retryCount, String failureCode) {
            failed.add(new Failed(eventId, retryCount, failureCode));
        }

        private List<String> failureCodes() {
            return failed.stream().map(Failed::failureCode).toList();
        }
    }

    private static final class FakeCompletionRepository implements CompletionRepository {
        private final Map<UUID, Completion> byEnrollment = new LinkedHashMap<>();
        private RuntimeException failure;

        @Override public void save(Completion completion) { throw new UnsupportedOperationException(); }
        @Override public Optional<Completion> findById(UUID completionId) {
            return byEnrollment.values().stream()
                .filter(value -> value.id().equals(completionId)).findFirst();
        }
        @Override public Optional<Completion> findByIdForUpdate(UUID completionId) {
            return findById(completionId);
        }
        @Override public Optional<Completion> findByEnrollmentId(UUID enrollmentId) {
            if (failure != null) throw failure;
            return Optional.ofNullable(byEnrollment.get(enrollmentId));
        }
        @Override public Optional<Completion> findByEnrollmentIdForUpdate(UUID enrollmentId) {
            throw new UnsupportedOperationException();
        }
        @Override public boolean updateEvaluation(Completion completion, CompletionStatus expectedStatus) {
            throw new UnsupportedOperationException();
        }
        @Override public boolean confirm(Completion completion, CompletionStatus expectedStatus) {
            throw new UnsupportedOperationException();
        }
        @Override public boolean revertConfirmation(Completion completion, CompletionStatus expectedStatus) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class FakeCredentialGroupRepository implements CredentialGroupRepository {
        private final Map<UUID, CredentialGroup> byCompletion = new LinkedHashMap<>();
        private final List<CredentialGroup> inserted = new ArrayList<>();

        @Override public Optional<CredentialGroup> findById(UUID groupId) {
            return byCompletion.values().stream().filter(value -> value.id().equals(groupId)).findFirst();
        }
        @Override public Optional<CredentialGroup> findByCompletionId(UUID completionId) {
            return Optional.ofNullable(byCompletion.get(completionId));
        }
        @Override public Optional<CredentialGroup> findByCompletionIdForUpdate(UUID completionId) {
            return Optional.ofNullable(byCompletion.get(completionId));
        }
        @Override public void insert(CredentialGroup group) {
            byCompletion.put(group.completionId(), group);
            inserted.add(group);
        }
    }

    private record Rescheduled(UUID eventId, int retryCount, Instant nextRetryAt, String failureCode) {
    }

    private record Failed(UUID eventId, int retryCount, String failureCode) {
    }
}
