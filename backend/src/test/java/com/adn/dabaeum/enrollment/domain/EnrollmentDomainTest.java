package com.adn.dabaeum.enrollment.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EnrollmentDomainTest {

    private static final Instant APPLIED_AT = Instant.parse("2026-08-01T00:00:00Z");
    private static final Instant APPROVED_AT = Instant.parse("2026-08-01T01:00:00Z");
    private static final Instant REJECTED_AT = Instant.parse("2026-08-01T02:00:00Z");
    private static final Instant CANCELLED_AT = Instant.parse("2026-08-01T03:00:00Z");
    private static final Instant WITHDRAWN_AT = Instant.parse("2026-08-01T04:00:00Z");

    @Test
    void preservesNullableAppliedByAndNormalizesReasons() {
        Enrollment rejected = enrollment(
            EnrollmentStatus.REJECTED, null, REJECTED_AT, null, null, "  사유  ", null);

        assertThat(rejected.appliedBy()).isNull();
        assertThat(rejected.rejectionReason()).isEqualTo("사유");
    }

    @Test
    void enforcesStatusTimestampAndReasonConsistency() {
        assertThatThrownBy(() -> enrollment(
            EnrollmentStatus.APPLIED, APPROVED_AT, null, null, null, null, null))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> enrollment(
            EnrollmentStatus.APPROVED, null, null, null, null, null, null))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> enrollment(
            EnrollmentStatus.REJECTED, null, null, null, null, "사유", null))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> enrollment(
            EnrollmentStatus.REJECTED, null, REJECTED_AT, null, null, " ", null))
            .isInstanceOf(IllegalArgumentException.class);
        assertThat(new Enrollment(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null,
            EnrollmentApplicationType.SELF, EnrollmentStatus.CANCELLED, APPLIED_AT, null,
            null, CANCELLED_AT, null, null, null, APPLIED_AT, APPLIED_AT))
            .isNotNull();
        assertThatThrownBy(() -> enrollment(
            EnrollmentStatus.WITHDRAWN, null, null, null, WITHDRAWN_AT, null, null))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> enrollment(
            EnrollmentStatus.APPROVED, APPROVED_AT, null, null, null, null, "취소 사유"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void requiresApprovedTimestampToBePreservedWhenWithdrawn() {
        Enrollment withdrawn = enrollment(
            EnrollmentStatus.WITHDRAWN, APPROVED_AT, null, null, WITHDRAWN_AT, null, null);

        assertThat(withdrawn.approvedAt()).isEqualTo(APPROVED_AT);
        assertThat(withdrawn.withdrawnAt()).isEqualTo(WITHDRAWN_AT);
    }

    @Test
    void enforcesReasonLength() {
        assertThatThrownBy(() -> enrollment(
            EnrollmentStatus.REJECTED, null, REJECTED_AT, null, null, "x".repeat(1001), null))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> enrollment(
            EnrollmentStatus.CANCELLED, null, null, CANCELLED_AT, null, null, "x".repeat(1001)))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void allowsOnlyDefinedLifecycleTransitions() {
        assertThat(EnrollmentStatusPolicy.approve(EnrollmentStatus.APPLIED))
            .isEqualTo(EnrollmentStatus.APPROVED);
        assertThat(EnrollmentStatusPolicy.approve(EnrollmentStatus.WAITLISTED))
            .isEqualTo(EnrollmentStatus.APPROVED);
        assertThat(EnrollmentStatusPolicy.reject(EnrollmentStatus.APPLIED))
            .isEqualTo(EnrollmentStatus.REJECTED);
        assertThat(EnrollmentStatusPolicy.cancel(EnrollmentStatus.WAITLISTED))
            .isEqualTo(EnrollmentStatus.CANCELLED);
        assertThat(EnrollmentStatusPolicy.withdraw(EnrollmentStatus.APPROVED))
            .isEqualTo(EnrollmentStatus.WITHDRAWN);

        assertThatThrownBy(() -> EnrollmentStatusPolicy.approve(EnrollmentStatus.APPROVED))
            .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> EnrollmentStatusPolicy.reject(EnrollmentStatus.REJECTED))
            .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> EnrollmentStatusPolicy.cancel(EnrollmentStatus.APPROVED))
            .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> EnrollmentStatusPolicy.withdraw(EnrollmentStatus.APPLIED))
            .isInstanceOf(IllegalStateException.class);
    }

    private Enrollment enrollment(
        EnrollmentStatus status,
        Instant approvedAt,
        Instant rejectedAt,
        Instant cancelledAt,
        Instant withdrawnAt,
        String rejectionReason,
        String cancellationReason
    ) {
        return new Enrollment(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null,
            EnrollmentApplicationType.SELF, status, APPLIED_AT, approvedAt, rejectedAt,
            cancelledAt, withdrawnAt, rejectionReason, cancellationReason,
            APPLIED_AT, APPLIED_AT
        );
    }
}
