package com.adn.dabaeum.completion.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CompletionDomainTest {

    private static final Instant NOW = Instant.parse("2026-08-05T00:00:00Z");

    @Test
    void validatesStateInvariants() {
        UUID id = UUID.randomUUID();
        UUID enrollmentId = UUID.randomUUID();

        Completion pending = completion(id, enrollmentId, CompletionStatus.PENDING_EVALUATION,
            null, null, null, null, null);
        assertThat(pending.status()).isEqualTo(CompletionStatus.PENDING_EVALUATION);

        Completion eligible = completion(id, enrollmentId, CompletionStatus.ELIGIBLE,
            NOW, null, null, null, null);
        assertThat(eligible.evaluatedAt()).isEqualTo(NOW);

        Completion notCompleted = completion(id, enrollmentId, CompletionStatus.NOT_COMPLETED,
            NOW, null, null, null, "출석률 부족");
        assertThat(notCompleted.failureReason()).isEqualTo("출석률 부족");

        Completion completed = completion(id, enrollmentId, CompletionStatus.COMPLETED,
            NOW, NOW.plusSeconds(1), UUID.randomUUID(), NOW.plusSeconds(1), null);
        assertThat(completed.completedAt()).isEqualTo(NOW.plusSeconds(1));
    }

    @Test
    void rejectsInvalidStateCombinationsAndValues() {
        UUID id = UUID.randomUUID();
        UUID enrollmentId = UUID.randomUUID();
        UUID actor = UUID.randomUUID();

        assertThatIllegalArgumentException().isThrownBy(() ->
            completion(id, enrollmentId, CompletionStatus.PENDING_EVALUATION,
                NOW, null, null, null, null));
        assertThatIllegalArgumentException().isThrownBy(() ->
            completion(id, enrollmentId, CompletionStatus.ELIGIBLE,
                NOW, null, null, null, "사유"));
        assertThatIllegalArgumentException().isThrownBy(() ->
            completion(id, enrollmentId, CompletionStatus.NOT_COMPLETED,
                null, null, null, null, "사유"));
        assertThatIllegalArgumentException().isThrownBy(() ->
            completion(id, enrollmentId, CompletionStatus.COMPLETED,
                NOW, null, actor, NOW, null));
        assertThatIllegalArgumentException().isThrownBy(() ->
            completion(id, enrollmentId, CompletionStatus.CANCELLED,
                NOW, null, null, null, null));
        assertThatIllegalArgumentException().isThrownBy(() ->
            new Completion(id, enrollmentId, CompletionStatus.ELIGIBLE,
                new BigDecimal("100.01"), 0, null, NOW, null, null, null, null, NOW, NOW));
        assertThatIllegalArgumentException().isThrownBy(() ->
            new Completion(id, enrollmentId, CompletionStatus.ELIGIBLE,
                BigDecimal.ZERO, -1, null, NOW, null, null, null, null, NOW, NOW));
        assertThatIllegalArgumentException().isThrownBy(() ->
            new Completion(id, enrollmentId, CompletionStatus.ELIGIBLE,
                BigDecimal.ZERO, 0, new BigDecimal("1000.00"), NOW, null, null, null, null, NOW, NOW));
        assertThatIllegalArgumentException().isThrownBy(() ->
            new Completion(id, enrollmentId, CompletionStatus.NOT_COMPLETED,
                BigDecimal.ZERO, 0, null, NOW, null, null, null, " ", NOW, NOW));
    }

    @Test
    void appliesExplicitEvaluationAndConfirmationPolicy() {
        CompletionStatusPolicy policy = new CompletionStatusPolicy();

        assertThat(policy.canEvaluate(CompletionStatus.PENDING_EVALUATION)).isTrue();
        assertThat(policy.canEvaluate(CompletionStatus.ELIGIBLE)).isTrue();
        assertThat(policy.canEvaluate(CompletionStatus.NOT_COMPLETED)).isTrue();
        assertThat(policy.canEvaluate(CompletionStatus.COMPLETED)).isFalse();
        assertThat(policy.canEvaluate(CompletionStatus.CANCELLED)).isFalse();
        assertThat(policy.canConfirm(CompletionStatus.ELIGIBLE)).isTrue();
        assertThat(policy.canConfirm(CompletionStatus.COMPLETED)).isFalse();
        assertThat(policy.canConfirm(CompletionStatus.CANCELLED)).isFalse();
    }

    private Completion completion(
        UUID id,
        UUID enrollmentId,
        CompletionStatus status,
        Instant evaluatedAt,
        Instant completedAt,
        UUID confirmedBy,
        Instant confirmedAt,
        String failureReason
    ) {
        return new Completion(id, enrollmentId, status, BigDecimal.valueOf(75).setScale(2), 60,
            new BigDecimal("1.00"), evaluatedAt, completedAt, confirmedBy, confirmedAt,
            failureReason, NOW, NOW);
    }
}
